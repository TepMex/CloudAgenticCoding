import { defineConfig } from "vite";

/** Project Pages base, e.g. `/CloudAgenticCoding/hanzi-reading-roguelike/` (see CI). */
const base = process.env.GH_PAGES_PUBLIC_PATH?.replace(/\/?$/, "/") ?? "./";

export default defineConfig({
  base,
  server: {
    host: true,
    port: 5173,
  },
  build: {
    target: "es2022",
    sourcemap: true,
  },
});
