import '@fontsource/nunito/cyrillic-400.css'
import '@fontsource/nunito/cyrillic-600.css'
import '@fontsource/nunito/cyrillic-700.css'
import '@fontsource/nunito/cyrillic-800.css'
import '@fontsource/nunito/latin-400.css'
import '@fontsource/nunito/latin-700.css'
import '@fontsource/playfair-display/cyrillic-700.css'
import '@fontsource/playfair-display/latin-700.css'
import '@fontsource/caveat/cyrillic-600.css'
import '@fontsource/caveat/latin-600.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { ToastProvider } from './components/Toast'
import { AccountProvider } from './store'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AccountProvider>
      <ToastProvider>
        <App />
      </ToastProvider>
    </AccountProvider>
  </StrictMode>,
)
