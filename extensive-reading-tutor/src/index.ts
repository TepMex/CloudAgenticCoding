import { serve } from "bun";
import path from "path";
import index from "./index.html";

const srcDir = import.meta.dir;

const server = serve({
  port: Number(process.env.PORT) || 3000,
  routes: {
    "/logo.svg": () => new Response(Bun.file(path.join(srcDir, "logo.svg")), { headers: { "Content-Type": "image/svg+xml" } }),
    "/*": index,
  },
  development: process.env.NODE_ENV !== "production" && {
    hmr: true,
    console: true,
  },
});

console.log(`Extensive reading tutor at ${server.url}`);
