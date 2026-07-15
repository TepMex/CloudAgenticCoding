import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

/** Project Pages base, e.g. `/CloudAgenticCoding/map-of-chinese/` (see CI). */
const base = process.env.GH_PAGES_PUBLIC_PATH?.replace(/\/?$/, "/") ?? "./";

export default defineConfig({
  plugins: [react()],
  base,
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: true,
    testTimeout: 15_000,
  },
});
