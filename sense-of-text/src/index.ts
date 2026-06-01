import { serve } from "bun";
import path from "path";
import { normalizeApiToken, validateApiToken } from "./lib/api-token";
import index from "./index.html";

const isDev = process.env.NODE_ENV !== "production";
const embeddingWorkerEntry = path.join(import.meta.dir, "lib/embeddings/worker.ts");
let cachedEmbeddingWorkerJs: string | undefined;

async function bundleEmbeddingWorker(): Promise<string> {
  if (!isDev && cachedEmbeddingWorkerJs) return cachedEmbeddingWorkerJs;

  const result = await Bun.build({
    entrypoints: [embeddingWorkerEntry],
    target: "browser",
    format: "esm",
  });

  if (!result.success) {
    throw new Error(result.logs.map(log => log.message).join("\n"));
  }

  const js = await result.outputs[0]!.text();
  if (!isDev) cachedEmbeddingWorkerJs = js;
  return js;
}

const server = serve({
  routes: {
    "/embeddings-worker.js": {
      async GET() {
        try {
          const js = await bundleEmbeddingWorker();
          return new Response(js, {
            headers: {
              "Content-Type": "application/javascript; charset=utf-8",
              "Cache-Control": isDev ? "no-store" : "public, max-age=31536000, immutable",
            },
          });
        } catch (err) {
          return Response.json(
            {
              error: {
                message:
                  err instanceof Error ? err.message : "Failed to bundle embeddings worker",
              },
            },
            { status: 500 },
          );
        }
      },
    },

    "/api/chat": {
      async POST(req) {
        try {
          const body = (await req.json()) as {
            baseUrl?: string;
            token?: string;
            model?: string;
            messages?: unknown[];
            responseFormat?: { type: string };
          };

          const baseUrl = body.baseUrl?.replace(/\/$/, "");
          const token = typeof body.token === "string" ? normalizeApiToken(body.token) : "";
          const model = body.model;
          const messages = body.messages;

          if (!baseUrl || !model || !messages) {
            return Response.json(
              { error: { message: "Missing baseUrl, token, model, or messages" } },
              { status: 400 },
            );
          }

          const tokenError = validateApiToken(token);
          if (tokenError) {
            return Response.json({ error: { message: tokenError } }, { status: 400 });
          }

          const url = `${baseUrl}/v1/chat/completions`;
          const upstream = await fetch(url, {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${token}`,
            },
            body: JSON.stringify({
              model,
              messages,
              response_format: body.responseFormat,
              temperature: 0.2,
            }),
          });

          const data = await upstream.json();
          return Response.json(data, { status: upstream.status });
        } catch (err) {
          const message =
            err instanceof Error && err.message.includes("invalid value")
              ? "Invalid API token for Authorization header. Paste an ASCII API key (e.g. sk-...) in Settings, not analysis text."
              : err instanceof Error
                ? err.message
                : "Proxy request failed";
          return Response.json({ error: { message } }, { status: 500 });
        }
      },
    },

    "/*": index,
  },

  development: process.env.NODE_ENV !== "production" && {
    hmr: true,
    console: true,
  },
});

console.log(`Server running at ${server.url}`);
