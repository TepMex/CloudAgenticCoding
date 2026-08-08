import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './styles.css'

/** Public-folder URLs must respect Vite `base` (Pages path or `./` for Android). */
const asset = (path: string) => `${import.meta.env.BASE_URL}${path.replace(/^\//, '')}`
const rootStyle = document.documentElement.style
rootStyle.setProperty('--bg-garden-map', `url(${JSON.stringify(asset('assets/garden-map.png'))})`)
rootStyle.setProperty(
  '--bg-battle',
  `url(${JSON.stringify(asset('assets/cleaning-court-clear.png'))})`,
)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
