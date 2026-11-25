import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

/** Project Pages base, e.g. `/CloudAgenticCoding/ci-yun/` (see CI). */
const base = process.env.GH_PAGES_PUBLIC_PATH?.replace(/\/?$/, "/") ?? "./";

export default defineConfig({
  plugins: [react()],
  base,
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: true,
    exclude: ["scripts/**", "node_modules/**", "dist/**"],
  },
});
