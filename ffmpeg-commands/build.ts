import plugin from "bun-plugin-tailwind";
import { cp, rm } from "node:fs/promises";
import path from "node:path";

const root = import.meta.dir;
const raw =
  process.argv.find(arg => arg.startsWith("--public-path="))?.slice("--public-path=".length) ??
  process.env.GH_PAGES_PUBLIC_PATH ??
  "./";
const publicPath = raw.endsWith("/") ? raw : `${raw}/`;
const outdir = path.join(root, "dist");

await rm(outdir, { recursive: true, force: true });

const entrypoints = [...new Bun.Glob("**.html").scanSync(path.join(root, "src"))].map(file =>
  path.resolve(root, "src", file),
);

const result = await Bun.build({
  entrypoints,
  outdir,
  plugins: [plugin],
  minify: true,
  target: "browser",
  sourcemap: "linked",
  publicPath,
  define: {
    "process.env.NODE_ENV": JSON.stringify("production"),
  },
});

if (!result.success) {
  for (const log of result.logs) console.error(log);
  process.exit(1);
}

await cp(path.join(root, "data/corpus.json"), path.join(outdir, "corpus.json"));
await cp(path.join(root, "data/embeddings.json"), path.join(outdir, "embeddings.json"));
await cp(path.join(root, "data/embeddings.f32"), path.join(outdir, "embeddings.f32"));
await cp(path.join(root, "node_modules/@litert-lm/core/wasm"), path.join(outdir, "vendor/litertlm"), {
  recursive: true,
});

for (const output of result.outputs) console.log(path.relative(root, output.path));
console.log(`public path ${publicPath}`);
console.log("copied corpus, embeddings, and LiteRT wasm");
