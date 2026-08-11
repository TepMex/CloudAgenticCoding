/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly BASE_URL: string
  /** GitHub raw (or Pages) base for heavy map/battle art when omitted from the APK. */
  readonly VITE_REMOTE_ASSET_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.css'
