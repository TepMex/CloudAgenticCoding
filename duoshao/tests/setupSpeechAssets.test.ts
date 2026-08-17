import { afterEach, describe, expect, test } from "bun:test";
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

const temporaryRoots: string[] = [];
const script = join(import.meta.dir, "..", "scripts", "setup-speech-assets.sh");

async function run(command: string[], env: Record<string, string> = {}) {
  const process = Bun.spawn(command, {
    cwd: join(import.meta.dir, ".."),
    env: { ...Bun.env, ...env },
    stdout: "pipe",
    stderr: "pipe",
  });
  const [exitCode, stdout, stderr] = await Promise.all([
    process.exited,
    new Response(process.stdout).text(),
    new Response(process.stderr).text(),
  ]);
  return { exitCode, stdout, stderr };
}

async function createRuntimeArchive(fixture: string): Promise<string> {
  const packageDirectory = join(fixture, "package");
  await mkdir(packageDirectory, { recursive: true });
  await writeFile(join(packageDirectory, "sherpa-onnx-wasm-nodejs.wasm"), "fake-wasm");
  await writeFile(join(packageDirectory, "sherpa-onnx-wasm-nodejs.js"), [
    "var Module=(()=>{",
    'var nodePath=require("path");',
    'if(!ENVIRONMENT_IS_NODE){throw new Error("NODERAWFS is currently only supported on Node.js environment.")}',
    "var _wrapNodeError=x=>x;var VFS={};",
    "for(var _key in NODERAWFS){FS[_key]=_wrapNodeError(NODERAWFS[_key])}",
    "return ()=>({});})();",
    'if(typeof exports==="object"&&typeof module==="object"){module.exports=Module;}',
  ].join(""));
  await writeFile(join(packageDirectory, "sherpa-onnx-asr.js"), [
    "function createOnlineRecognizer(){}\n",
    "class OfflineRecognizer{}\n",
    "if (typeof process == 'object' && typeof process.versions == 'object') { module.exports = { createOnlineRecognizer, OfflineRecognizer }; }\n",
  ].join(""));
  const archive = join(fixture, "runtime.tgz");
  expect((await run(["tar", "-czf", archive, "-C", fixture, "package"])).exitCode).toBe(0);
  return archive;
}

afterEach(async () => {
  await Promise.all(temporaryRoots.splice(0).map((path) => rm(path, { recursive: true, force: true })));
});

describe("setup-speech-assets.sh", () => {
  test("supports a side-effect-free dry run", async () => {
    const root = await mkdtemp(join(tmpdir(), "duoshao-setup-dry-"));
    temporaryRoots.push(root);
    const result = await run(["bash", script, "--dry-run"], { DUOSHAO_ROOT: root });
    expect(result.exitCode).toBe(0);
    expect(result.stdout).toContain("dry run");
    expect(await Bun.file(join(root, ".env.local")).exists()).toBe(false);
  });

  test("extracts weights, installs a bridge and preserves unrelated env values", async () => {
    const root = await mkdtemp(join(tmpdir(), "duoshao-setup-"));
    const fixture = await mkdtemp(join(tmpdir(), "duoshao-model-"));
    temporaryRoots.push(root, fixture);
    const modelDirectory = join(fixture, "test-paraformer");
    await mkdir(modelDirectory);
    await writeFile(join(modelDirectory, "model.int8.onnx"), "fake-onnx-weights");
    await writeFile(join(modelDirectory, "tokens.txt"), "零 0\n一 1\n");
    const archive = join(fixture, "model.tar.bz2");
    const tar = await run(["tar", "-cjf", archive, "-C", fixture, "test-paraformer"]);
    expect(tar.exitCode).toBe(0);

    const bridge = join(fixture, "bridge.js");
    await writeFile(bridge, "export function createParaformerRecognizer() {}\n");
    await writeFile(join(root, ".env.local"), "KEEP_THIS=value\nVITE_PARAFORMER_MODEL_URL=/old.onnx\n");

    const result = await run([
      "bash", script,
      "--model-url", `file://${archive}`,
      "--runtime-source", bridge,
      "--force",
    ], { DUOSHAO_ROOT: root });

    expect(result.exitCode).toBe(0);
    expect(result.stderr).not.toContain("setup:speech:");
    expect(await readFile(join(root, "public/speech/model.int8.onnx"), "utf8")).toBe("fake-onnx-weights");
    expect(await readFile(join(root, "public/speech/tokens.txt"), "utf8")).toContain("一 1");
    expect(await readFile(join(root, "public/speech/sherpa-paraformer-bridge.js"), "utf8")).toContain("createParaformerRecognizer");

    const environment = await readFile(join(root, ".env.local"), "utf8");
    expect(environment).toContain("KEEP_THIS=value");
    expect(environment).toContain("VITE_SHERPA_RUNTIME_URL=/speech/sherpa-paraformer-bridge.js");
    expect(environment).toContain("VITE_PARAFORMER_MODEL_URL=/speech/model.int8.onnx");
    expect(environment.match(/VITE_PARAFORMER_MODEL_URL=/g)).toHaveLength(1);
  });

  test("automatically installs the local browser runtime", async () => {
    const root = await mkdtemp(join(tmpdir(), "duoshao-setup-runtime-"));
    const fixture = await mkdtemp(join(tmpdir(), "duoshao-runtime-model-"));
    temporaryRoots.push(root, fixture);
    const modelDirectory = join(fixture, "test-paraformer");
    await mkdir(modelDirectory);
    await writeFile(join(modelDirectory, "model.int8.onnx"), "fake-onnx-weights");
    await writeFile(join(modelDirectory, "tokens.txt"), "零 0\n");
    const archive = join(fixture, "model.tar.bz2");
    expect((await run(["tar", "-cjf", archive, "-C", fixture, "test-paraformer"])).exitCode).toBe(0);
    const runtimeArchive = await createRuntimeArchive(join(fixture, "runtime-fixture"));

    const result = await run([
      "bash", script,
      "--model-url", `file://${archive}`,
      "--runtime-package-url", `file://${runtimeArchive}`,
      "--force",
    ], { DUOSHAO_ROOT: root });

    expect(result.exitCode).toBe(0);
    expect(await Bun.file(join(root, "public/speech/sherpa-paraformer-bridge.js")).exists()).toBe(true);
    expect(await Bun.file(join(root, "public/speech/sherpa-onnx-wasm.wasm")).exists()).toBe(true);
    expect(await readFile(join(root, ".env.local"), "utf8")).toContain("VITE_SHERPA_RUNTIME_URL=/speech/sherpa-paraformer-bridge.js");

    await rm(join(root, ".env.local"));
    const weightsOnly = await run([
      "bash", script,
      "--model-url", `file://${archive}`,
      "--weights-only",
      "--force",
    ], { DUOSHAO_ROOT: root });
    expect(weightsOnly.exitCode).toBe(0);
    expect(await Bun.file(join(root, "public/speech/model.int8.onnx")).exists()).toBe(true);
    expect(await Bun.file(join(root, ".env.local")).exists()).toBe(false);
  });
});
