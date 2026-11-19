/* KUPO POS - Service Worker (PWA) V1.5.0: permite usar la app sin internet. */
const CACHE = 'kupo-pos-v85'; /* v85: descuentos opcionales por negocio (interruptor en Opciones de esta licencia, solo Administrador, % o $ al cobrar, sale en el ticket) · v84: revisión periódica de Mensajes en segundo plano + ticket como imagen (canvas PNG para WhatsApp) · v83: casilla "Abrir la ventana de imprimir al cobrar" en el móvil (Personalizar ticket, apagada por default) · v82: chat interno "Mensajes" (solo texto, Admin <-> Cajeros, Realtime + polling 20 s) · v81: botón Sincronizar en el encabezado (siempre visible, con insignia de pendientes) · v80: botón Enviar ticket por WhatsApp (wa.me) + texto plano del ticket · v79: instalación resistente — cada archivo se guarda por separado y si uno falla (internet lento) el service worker igual se instala, para que Chrome/Edge ofrezcan instalar la app */ /* v75: licencia ya no se prellena (campo vacío al abrir la pantalla) + iconos PNG 192/512 en el manifiesto y en caché para que Chrome ofrezca instalar la app */
const ASSETS = ['./', 'index.html', 'admin.html', 'manifest.json', 'movil.html', 'manifest-movil.json', 'icon.svg', 'icon-maskable.svg', 'icon-192.png', 'icon-512.png', 'icon-maskable-512.png'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) =>
      Promise.all(ASSETS.map((url) => cache.add(url).catch(function () { /* v79: un archivo que falle no tumba la instalación */ })))
    ).then(() => self.skipWaiting())
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
