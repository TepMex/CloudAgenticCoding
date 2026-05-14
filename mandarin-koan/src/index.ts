import { serve } from "bun";
import path from "path";
import index from "./index.html";

const srcDir = import.meta.dir;

function staticFile(rel: string, contentType: string) {
  return () => new Response(Bun.file(path.join(srcDir, rel)), { headers: { "Content-Type": contentType } });
}

const server = serve({
  routes: {
    "/logo.svg": staticFile("logo.svg", "image/svg+xml"),

    // Serve index.html for all unmatched routes.
    "/*": index,

    "/api/hello": {
      async GET() {
        return Response.json({
          message: "Hello, world!",
          method: "GET",
        });
      },
      async PUT() {
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
    hmr: true,
    console: true,
  },
});

console.log(`🚀 Server running at ${server.url}`);
