/* KUPO POS - Service Worker (PWA) V1.5.0: permite usar la app sin internet. */
const CACHE = 'kupo-pos-v28'; /* v28: en móvil (hasta 760px) la app ocupa el 100% del ancho; columna centrada máx. 480px solo en escritorio */
const ASSETS = ['./', 'index.html', 'admin.html', 'manifest.json', 'movil.html', 'manifest-movil.json'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

function isHtml(request) {
  const url = new URL(request.url);
  return request.mode === 'navigate' || url.pathname.endsWith('.html') || url.pathname === '/' || url.pathname.endsWith('/');
}

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  // NETWORK-FIRST para el HTML: siempre intenta la versión nueva; si no hay
  // internet, cae al caché. Así una actualización nunca se queda atorada en v17/v18.
  if (isHtml(event.request)) {
    event.respondWith(
      fetch(event.request).then((response) => {
        if (response && response.ok && new URL(event.request.url).origin === self.location.origin) {
          const copy = response.clone();
          caches.open(CACHE).then((cache) => cache.put(event.request, copy));
        }
        return response;
      }).catch(() => caches.match(event.request, { ignoreSearch: true }).then((cached) => cached || caches.match('index.html')))
    );
    return;
  }
  // Cache-first solo para lo demás (manifest, iconos): no cambia entre versiones.
  event.respondWith(
    caches.match(event.request, { ignoreSearch: true }).then((cached) => {
      if (cached) return cached;
      return fetch(event.request).then((response) => {
        if (response && response.ok && new URL(event.request.url).origin === self.location.origin) {
          const copy = response.clone();
          caches.open(CACHE).then((cache) => cache.put(event.request, copy));
        }
        return response;
      }).catch(() => undefined);
    })
  );
});
