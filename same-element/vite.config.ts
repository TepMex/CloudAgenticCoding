import { spawnSync } from 'node:child_process'
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'

/** Project Pages base, e.g. `/CloudAgenticCoding/same-element/` (see CI). */
const base = process.env.GH_PAGES_PUBLIC_PATH?.replace(/\/?$/, '/') ?? './'

function prepareHanziPlugin(): Plugin {
  let prepared = false
  const run = () => {
    if (prepared) return
    prepared = true
    const result = spawnSync('bun', ['scripts/prepare-hanzi.ts'], { stdio: 'inherit' })
    if (result.status !== 0) {
      throw new Error('prepare-hanzi failed')
    }
  }
  return {
    name: 'prepare-hanzi',
    buildStart: run,
    configureServer: run,
  }
}

export default defineConfig({
  plugins: [prepareHanziPlugin(), react()],
  base,
})
