import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { assetUrl } from './assetUrl'
import './styles.css'

const rootStyle = document.documentElement.style
rootStyle.setProperty('--bg-garden-map', `url(${JSON.stringify(assetUrl('assets/garden-map.png'))})`)
rootStyle.setProperty(
  '--bg-battle',
  `url(${JSON.stringify(assetUrl('assets/cleaning-court-clear.png'))})`,
)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
