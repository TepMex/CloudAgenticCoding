import { defineConfig } from 'vite';

/** Project Pages base, e.g. `/CloudAgenticCoding/han-count-me/` (see CI). */
const base = process.env.GH_PAGES_PUBLIC_PATH?.replace(/\/?$/, '/') ?? './';

export default defineConfig({
  base,
  build: {
    target: 'es2022',
  },
  server: {
    port: 4173,
    strictPort: true,
  },
});
