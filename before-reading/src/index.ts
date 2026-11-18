import { serve } from 'bun'
import index from './index.html'

const server = serve({
  port: Number(process.env.PORT ?? 4173),
  hostname: '0.0.0.0',
  routes: {
    '/*': index,
  },
  development: process.env.NODE_ENV !== 'production' && {
    hmr: true,
    console: true,
  },
})

console.log(`before-reading ${server.url}`)
