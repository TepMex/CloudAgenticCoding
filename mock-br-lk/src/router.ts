import { useSyncExternalStore } from 'react'

function currentPath(): string {
  const raw = window.location.hash.replace(/^#/, '')
  return raw.startsWith('/') ? raw : '/'
}

function subscribe(cb: () => void): () => void {
  window.addEventListener('hashchange', cb)
  return () => window.removeEventListener('hashchange', cb)
}

export function useRoute(): string {
  return useSyncExternalStore(subscribe, currentPath)
}

export function navigate(path: string, { replace = false } = {}): void {
  if (currentPath() === path) return
  if (replace) {
    const url = new URL(window.location.href)
    url.hash = path
    window.history.replaceState(null, '', url)
    window.dispatchEvent(new HashChangeEvent('hashchange'))
  } else {
    window.location.hash = path
  }
}
