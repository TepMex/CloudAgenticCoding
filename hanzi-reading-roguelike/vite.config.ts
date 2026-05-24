import { defineConfig } from "vite";
import { appendFileSync } from "node:fs";

const DEBUG_LOG_PATH = "/opt/cursor/logs/debug.log";

export default defineConfig({
  plugins: [
    {
      name: "debug-log-endpoint",
      configureServer(server) {
        server.middlewares.use("/__debug-log", (req, res) => {
          if (req.method !== "POST") {
            res.statusCode = 405;
            res.end("method-not-allowed");
            return;
          }

          const chunks: Uint8Array[] = [];
          req.on("data", (chunk: Uint8Array) => chunks.push(chunk));
          req.on("end", () => {
            appendFileSync(DEBUG_LOG_PATH, Buffer.concat(chunks).toString("utf8"));
            res.statusCode = 204;
            res.end();
          });
        });
      }
    }
  ],
  server: {
    host: "0.0.0.0",
    port: 5173
  }
});
