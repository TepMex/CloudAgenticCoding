import { serve } from "bun";
import path from "path";
import index from "./index.html";

const srcDir = import.meta.dir;

function staticFile(rel: string, contentType: string) {
  return () => new Response(Bun.file(path.join(srcDir, rel)), { headers: { "Content-Type": contentType } });
}

const server = serve({
  routes: {
    "/manifest.webmanifest": staticFile("manifest.webmanifest", "application/manifest+json"),
    "/sw.js": staticFile("sw.js", "application/javascript; charset=utf-8"),
    "/pwa-icon-192.png": staticFile("pwa-icon-192.png", "image/png"),
    "/pwa-icon-512.png": staticFile("pwa-icon-512.png", "image/png"),
    "/logo.svg": staticFile("logo.svg", "image/svg+xml"),

    // Serve index.html for all unmatched routes.
    "/*": index,

    "/api/hello": {
      async GET(req) {
        return Response.json({
          message: "Hello, world!",
          method: "GET",
        });
      },
      async PUT(req) {
        return Response.json({
          message: "Hello, world!",
          method: "PUT",
        });
      },
    },

    "/api/hello/:name": async req => {
      const name = req.params.name;
      return Response.json({
        message: `Hello, ${name}!`,
      });
    },
  },

  development: process.env.NODE_ENV !== "production" && {
    // Enable browser hot reloading in development
    hmr: true,

    // Echo console logs from the browser to the server
    console: true,
  },
});

console.log(`🚀 Server running at ${server.url}`);
