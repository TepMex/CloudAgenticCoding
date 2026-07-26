/** Content-Security-Policy helpers and meta for API-key-bearing client apps. */

export const APP_CSP = [
  "default-src 'self'",
  "script-src 'self' 'wasm-unsafe-eval'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self'",
  "connect-src 'self' http: https:",
  "frame-src 'self' blob:",
  "worker-src 'self' blob:",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join("; ");

export function applyCspMeta(): void {
  let meta = document.querySelector('meta[http-equiv="Content-Security-Policy"]');
  if (!meta) {
    meta = document.createElement("meta");
    meta.setAttribute("http-equiv", "Content-Security-Policy");
    document.head.appendChild(meta);
  }
  meta.setAttribute("content", APP_CSP);
}
