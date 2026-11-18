#!/usr/bin/env bun
import { rm } from 'node:fs/promises'
import path from 'node:path'

const raw = process.argv.find((arg) => arg.startsWith('--public-path='))?.slice('--public-path='.length) ?? './'
const publicPath = raw.endsWith('/') ? raw : `${raw}/`
const outdir = path.join(process.cwd(), 'dist')

await rm(outdir, { recursive: true, force: true })

const entrypoints = [...new Bun.Glob('**.html').scanSync('src')].map((file) => path.resolve('src', file))
const result = await Bun.build({
  entrypoints,
  outdir,
  minify: true,
  target: 'browser',
  sourcemap: 'linked',
  publicPath,
  define: {
    'process.env.NODE_ENV': JSON.stringify('production'),
  },
})

if (!result.success) {
  for (const log of result.logs) console.error(log)
  process.exit(1)
}

for (const output of result.outputs) console.log(path.relative(process.cwd(), output.path))
