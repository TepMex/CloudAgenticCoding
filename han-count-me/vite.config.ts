import { defineConfig } from 'vite';
import { resolve } from 'node:path';

/** Project Pages base, e.g. `/CloudAgenticCoding/han-count-me/` (see CI). */
const base = process.env.GH_PAGES_PUBLIC_PATH?.replace(/\/?$/, '/') ?? './';

export default defineConfig({
  base,
  build: {
    target: 'es2022',
    rollupOptions: {
      input: {
        game: resolve(__dirname, 'index.html'),
        presentation: resolve(__dirname, 'presentation.html'),
      },
    },
  },
  server: {
    port: 4173,
    strictPort: true,
  },
});
