/**
 * Minimal offline-capable service worker. Uses network-first; caches responses
 * for same-origin GET so repeat visits work offline. scope follows install path
 * (e.g. /repo/socratic/ on GitHub Pages).
 */
const CACHE = "socratus-v1";

function scopeIndexUrl() {
  return new URL("index.html", self.registration.scope).href;
}

self.addEventListener("install", (event) => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE).then((cache) =>
      cache.add(new Request(scopeIndexUrl(), { cache: "reload" })).catch(() => {}),
    ),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      for (const key of await caches.keys()) {
        if (key !== CACHE) await caches.delete(key);
      }
      await self.clients.claim();
    })(),
  );
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET") return;

  let url;
  try {
    url = new URL(request.url);
  } catch {
    return;
  }
  if (url.origin !== self.location.origin) return;

  const scope = self.registration.scope;

  if (request.mode === "navigate") {
    event.respondWith(
      fetch(request)
        .then((res) => {
          if (res.ok) {
            const copy = res.clone();
            caches.open(CACHE).then((c) => c.put(scopeIndexUrl(), copy));
          }
          return res;
        })
        .catch(() =>
          caches.match(scopeIndexUrl()).then((r) => r || new Response("Offline", { status: 503 })),
        ),
    );
    return;
  }

  event.respondWith(
    fetch(request)
      .then((res) => {
        if (res.ok && request.url.startsWith(scope)) {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(request, copy));
        }
        return res;
      })
      .catch(() => caches.match(request).then((r) => r || Response.error())),
  );
});
