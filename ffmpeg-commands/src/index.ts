import { serve } from "bun";
import { stat } from "node:fs/promises";
import path from "path";
import index from "./index.html";

const root = path.join(import.meta.dir, "..");
const wasmDir = path.join(root, "node_modules/@litert-lm/core/wasm");
const port = Number(process.env.PORT) || 4731;

const types: Record<string, string> = {
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".wasm": "application/wasm",
  ".json": "application/json",
  ".data": "application/octet-stream",
  ".f32": "application/octet-stream",
};

function staticFile(filePath: string, contentType: string): Response {
  return new Response(Bun.file(filePath), {
    headers: {
      "Content-Type": contentType,
      "Cache-Control": "public, max-age=3600",
    },
  });
}

function safeJoin(base: string, requestPath: string): string | null {
  const relative = decodeURIComponent(requestPath);
  if (relative.includes("\0")) return null;
  const full = path.normalize(path.join(base, relative));
  if (full !== base && !full.startsWith(`${base}${path.sep}`)) return null;
  return full;
}

const server = serve({
  port,
  hostname: "0.0.0.0",
  routes: {
    "/": index,
    "/corpus.json": () => staticFile(path.join(root, "data/corpus.json"), "application/json"),
    "/embeddings.json": () => staticFile(path.join(root, "data/embeddings.json"), "application/json"),
    "/embeddings.f32": () => staticFile(path.join(root, "data/embeddings.f32"), "application/octet-stream"),
    "/vendor/litertlm/*": async request => {
      const url = new URL(request.url);
      const relative = url.pathname.replace(/^\/vendor\/litertlm\/?/, "");
      const filePath = safeJoin(wasmDir, relative);
      if (!filePath) return new Response("Bad path", { status: 400 });
      const info = await stat(filePath).catch(() => null);
      if (!info?.isFile()) return new Response("Not found", { status: 404 });
      const extension = path.extname(filePath);
      return staticFile(filePath, types[extension] ?? "application/octet-stream");
    },
  },
  development: process.env.NODE_ENV !== "production" && {
    hmr: true,
    console: true,
  },
});

console.log(`FFmpeg commands at ${server.url}`);
