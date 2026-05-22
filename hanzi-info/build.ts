#!/usr/bin/env bun
import plugin from "bun-plugin-tailwind";
import { existsSync } from "fs";
import { copyFile, readdir, readFile, rm, unlink, writeFile } from "fs/promises";
import path from "path";
import { writeDeployInfoTs } from "../scripts/write-deploy-info.ts";

const STATIC_FROM_SRC = ["logo.svg"] as const;
const STATIC_FROM_PUBLIC = ["hanzi-db.json"] as const;

if (process.argv.includes("--help") || process.argv.includes("-h")) {
  console.log(`
Usage: bun run build.ts [options]
See Bun.build docs for --public-path, --outdir, etc.
`);
  process.exit(0);
}

const toCamelCase = (str: string): string => str.replace(/-([a-z])/g, g => g[1]!.toUpperCase());

const parseValue = (value: string): unknown => {
  if (value === "true") return true;
  if (value === "false") return false;
  if (/^\d+$/.test(value)) return parseInt(value, 10);
  if (/^\d*\.\d+$/.test(value)) return parseFloat(value);
  if (value.includes(",")) return value.split(",").map(v => v.trim());
  return value;
};

function parseArgs(): Partial<Bun.BuildConfig> {
  const config: Partial<Bun.BuildConfig> = {};
  const args = process.argv.slice(2);
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === undefined || !arg.startsWith("--")) continue;

    if (arg.startsWith("--no-")) {
      config[toCamelCase(arg.slice(5))] = false;
      continue;
    }

    if (!arg.includes("=") && (i === args.length - 1 || args[i + 1]?.startsWith("--"))) {
      config[toCamelCase(arg.slice(2))] = true;
      continue;
    }

    let key: string;
    let value: string;
    if (arg.includes("=")) {
      [key, value] = arg.slice(2).split("=", 2) as [string, string];
    } else {
      key = arg.slice(2);
      value = args[++i] ?? "";
    }
    key = toCamelCase(key);
    if (key.includes(".")) {
      const [parentKey, childKey] = key.split(".");
      (config as Record<string, unknown>)[parentKey] = (config as Record<string, unknown>)[parentKey] || {};
      ((config as Record<string, unknown>)[parentKey] as Record<string, unknown>)[childKey] = parseValue(value);
    } else {
      (config as Record<string, unknown>)[key] = parseValue(value);
    }
  }
  return config;
}

const formatFileSize = (bytes: number): string => {
  const units = ["B", "KB", "MB", "GB"];
  let size = bytes;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex++;
  }
  return `${size.toFixed(2)} ${units[unitIndex]}`;
};

await writeDeployInfoTs(path.join(process.cwd(), "src/lib/deploy-info.ts"));

const cliConfig = parseArgs();
const outdir = cliConfig.outdir || path.join(process.cwd(), "dist");

if (existsSync(outdir)) {
  await rm(outdir, { recursive: true, force: true });
}

const start = performance.now();
const entrypoints = [...new Bun.Glob("**.html").scanSync("src")]
  .map(a => path.resolve("src", a))
  .filter(dir => !dir.includes("node_modules"));

const result = await Bun.build({
  entrypoints,
  outdir,
  plugins: [plugin],
  minify: true,
  target: "browser",
  sourcemap: "linked",
  define: {
    "process.env.NODE_ENV": JSON.stringify("production"),
  },
  ...cliConfig,
});

const end = performance.now();
console.table(
  result.outputs.map(output => ({
    File: path.relative(process.cwd(), output.path),
    Type: output.kind,
    Size: formatFileSize(output.size),
  })),
);

async function linkPwaIconsInManifests(outDir: string, outputs: typeof result.outputs): Promise<void> {
  const hashedByPlain = new Map<string, string>();
  for (const o of outputs) {
    const bn = path.basename(o.path);
    if (bn.startsWith("pwa-icon-192") && bn.endsWith(".png")) hashedByPlain.set("pwa-icon-192.png", bn);
    if (bn.startsWith("pwa-icon-512") && bn.endsWith(".png")) hashedByPlain.set("pwa-icon-512.png", bn);
  }
  if (hashedByPlain.size === 0) return;

  for (const f of await readdir(outDir)) {
    if (!f.endsWith(".webmanifest")) continue;
    const p = path.join(outDir, f);
    const manifest = JSON.parse(await readFile(p, "utf8")) as { icons?: { src: string }[] };
    if (!manifest.icons?.length) continue;
    let changed = false;
    for (const icon of manifest.icons) {
      const base = path.basename((icon.src.split("?")[0] ?? icon.src).replace(/^\.\//, ""));
      const hashed = hashedByPlain.get(base);
      if (hashed) {
        const next = `./${hashed}`;
        if (icon.src !== next) {
          icon.src = next;
          changed = true;
        }
      }
    }
    if (changed) await writeFile(p, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  }

  for (const [plain, hashed] of hashedByPlain) {
    if (plain === hashed) continue;
    const plainPath = path.join(outDir, plain);
    if (existsSync(plainPath)) {
      await unlink(plainPath);
      console.log(`🗑️ Removed duplicate icon copy: ${path.relative(process.cwd(), plainPath)}`);
    }
  }
}

for (const name of STATIC_FROM_SRC) {
  const from = path.join(process.cwd(), "src", name);
  const to = path.join(outdir, name);
  if (existsSync(from)) {
    await copyFile(from, to);
    console.log(`📎 Copied static asset: ${path.relative(process.cwd(), to)}`);
  }
}

for (const name of STATIC_FROM_PUBLIC) {
  const from = path.join(process.cwd(), "public", name);
  const to = path.join(outdir, name);
  if (existsSync(from)) {
    await copyFile(from, to);
    console.log(`📎 Copied public asset: ${path.relative(process.cwd(), to)}`);
  } else {
    console.warn(`⚠️ Missing ${from} — run bun run build:db first.`);
  }
}

await linkPwaIconsInManifests(outdir, result.outputs);
console.log(`\n✅ Build completed in ${(end - start).toFixed(2)}ms\n`);
