import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";
import path from "node:path";

const providerConnectSrc =
  // MVP: allow any https provider endpoint by default. The threat-model doc
  // describes the recommended stricter policy (per-profile base URL list).
  process.env.VITE_PROVIDER_CONNECT_SRC ?? "https:";

export default defineConfig({
  resolve: {
    alias: { "@": path.resolve(__dirname, "src") },
  },
  plugins: [
    react(),
    VitePWA({
      registerType: "prompt",
      includeAssets: ["favicon.svg"],
      manifest: {
        name: "看书朋友",
        short_name: "看书朋友",
        display: "standalone",
        background_color: "#1c1917",
        theme_color: "#1c1917",
        icons: [
          {
            src: "favicon.svg",
            sizes: "any",
            type: "image/svg+xml",
            purpose: "any",
          },
        ],
      },
      workbox: {
        globPatterns: ["**/*.{js,css,html,svg,woff2}"],
        navigateFallback: "index.html",
        cleanupOutdatedCaches: true,
      },
      devOptions: {
        enabled: false,
      },
    }),
  ],
  server: {
    headers: {
      "Content-Security-Policy": [
        "default-src 'self'",
        `connect-src 'self' ${providerConnectSrc}`,
        "img-src 'self' data: blob:",
        "style-src 'self' 'unsafe-inline'",
        "font-src 'self'",
        "frame-src 'self' blob:",
        "worker-src 'self'",
        "manifest-src 'self'",
        "object-src 'none'",
        "base-uri 'self'",
      ].join("; "),
    },
  },
  build: {
    target: "es2022",
    sourcemap: true,
    rollupOptions: {
      output: {
        manualChunks: {
          epub: ["epubjs"],
          react: ["react", "react-dom"],
        },
      },
    },
  },
  test: {
    environment: "node",
  },
} as any);

// Bun test preloads: provide a DOM for tests that need it.