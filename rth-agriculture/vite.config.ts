import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/** Project Pages base, e.g. `/CloudAgenticCoding/rth-agriculture/` (see CI). */
const base = process.env.GH_PAGES_PUBLIC_PATH?.replace(/\/?$/, '/') ?? './'

export default defineConfig({
  plugins: [react()],
  base,
})
