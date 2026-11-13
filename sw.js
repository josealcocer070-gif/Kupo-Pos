/* KUPO POS - Service Worker (PWA) V1.5.0: permite usar la app sin internet. */
const CACHE = 'kupo-pos-v16'; /* v16: Ticket ancho (440px) con foto y descripción por línea, buscador SCAN, cliente y caja abierta (estilo ORBI POS PRO, en azul KUPO V1.5.0); sesión del cajero persiste al recargar */
const ASSETS = ['./', 'index.html', 'admin.html', 'manifest.json'];

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

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  event.respondWith(
    caches.match(event.request, { ignoreSearch: true }).then((cached) => {
      if (cached) return cached;
      return fetch(event.request).then((response) => {
        if (response && response.ok && new URL(event.request.url).origin === self.location.origin) {
          const copy = response.clone();
          caches.open(CACHE).then((cache) => cache.put(event.request, copy));
        }
        return response;
      }).catch(() => {
        if (event.request.mode === 'navigate') return caches.match('index.html');
        return undefined;
      });
    })
  );
});
