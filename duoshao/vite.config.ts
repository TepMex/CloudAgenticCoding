import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const speechModules = new Set([
  "/speech/sherpa-paraformer-bridge.js",
  "/speech/sherpa-onnx-wasm.js",
  "/speech/sherpa-onnx-asr.js",
]);

export default defineConfig({
  plugins: [
    {
      name: "serve-local-speech-modules",
      configureServer(server) {
        server.middlewares.use(async (request, response, next) => {
          const pathname = request.url?.split("?", 1)[0];
          if (!pathname || !speechModules.has(pathname)) return next();
          try {
            response.statusCode = 200;
            response.setHeader("Content-Type", "text/javascript; charset=utf-8");
            response.setHeader("Cache-Control", "no-cache");
            response.end(await readFile(resolve(import.meta.dirname, `public${pathname}`)));
          } catch (error) {
            next(error as Error);
          }
        });
      },
    },
    react(),
  ],
  worker: { format: "es" },
});
