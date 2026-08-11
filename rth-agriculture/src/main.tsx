import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { applyGardenMapCssVar } from './assetUrl'
import './styles.css'

applyGardenMapCssVar()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
