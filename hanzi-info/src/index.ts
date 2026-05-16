import { serve } from "bun";
import path from "path";
import index from "./index.html";

const srcDir = import.meta.dir;
const publicDir = path.join(srcDir, "..", "public");

function staticFile(absPath: string, contentType: string) {
  return () => new Response(Bun.file(absPath), { headers: { "Content-Type": contentType } });
}

const server = serve({
  routes: {
    "/logo.svg": staticFile(path.join(srcDir, "logo.svg"), "image/svg+xml"),
    "/hanzi-db.json": staticFile(path.join(publicDir, "hanzi-db.json"), "application/json; charset=utf-8"),
    "/*": index,
  },

  development: process.env.NODE_ENV !== "production" && {
    hmr: true,
    console: true,
  },
});

console.log(`🚀 Hanzi Info dev server at ${server.url}`);
