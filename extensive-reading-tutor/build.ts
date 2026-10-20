#!/usr/bin/env bun
import { existsSync } from "fs";
import { copyFile, rm } from "fs/promises";
import path from "path";
import { writeDeployInfoTs } from "../scripts/write-deploy-info.ts";

const toCamelCase = (str: string): string => str.replace(/-([a-z])/g, group => group[1]!.toUpperCase());

function parseArgs(): Partial<Bun.BuildConfig> {
  const config: Partial<Bun.BuildConfig> = {};
  const args = process.argv.slice(2);
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (!arg?.startsWith("--")) continue;
    let key: string;
    let value: string;
    if (arg.includes("=")) {
      [key, value] = arg.slice(2).split("=", 2) as [string, string];
    } else {
      key = arg.slice(2);
      value = args[++i] ?? "true";
    }
    (config as Record<string, unknown>)[toCamelCase(key)] = value;
  }
  return config;
}

await writeDeployInfoTs(path.join(process.cwd(), "src/lib/deploy-info.ts"));

const cliConfig = parseArgs();
const outdir = typeof cliConfig.outdir === "string" ? cliConfig.outdir : path.join(process.cwd(), "dist");
if (existsSync(outdir)) await rm(outdir, { recursive: true, force: true });

const result = await Bun.build({
  entrypoints: [path.resolve("src/index.html")],
  outdir,
  minify: true,
  target: "browser",
  sourcemap: "linked",
  define: {
    "process.env.NODE_ENV": JSON.stringify("production"),
  },
  ...cliConfig,
});

if (!result.success) {
  console.error(result.logs);
  process.exit(1);
}

const logoFrom = path.join(process.cwd(), "src/logo.svg");
if (existsSync(logoFrom)) await copyFile(logoFrom, path.join(outdir, "logo.svg"));

console.log(`Built ${result.outputs.length} files into ${outdir}`);
