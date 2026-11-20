/* KUPO POS - Service Worker (PWA) V1.5.0: permite usar la app sin internet. */
const CACHE = 'kupo-pos-v98'; /* v91: configuración inicial del dueño (Lalo 2026-11-20): al validar una licencia sin cajeros se pide nombre, usuario y PIN del dueño en vez de sembrar el cajero de ejemplo Eduardo Benitez; las licencias con cajeros no cambian */  /* v90: impresión Bluetooth en PC (Lalo 2026-11-20): el módulo Bluetooth de movil.html (Web Bluetooth + ESC/POS + pulso del cajón) portado a index.html para que la tablet de Jonuta imprima desde la versión PC en horizontal; sin cambios en movil.html */ /* v89: periodo de gracia de 3 días tras el vencimiento de la renta mensual (Lalo 2026-11-20): la licencia sigue activa 3 días con aviso visible (banner + toast) y al 4º día se bloquea como antes; las vitalicias no se ven afectadas */ /* v88: Acciones rápidas de Ventas limpias — en Ventas quedan solo Poner en espera, Reimprimir, Mensajes, ticket por WhatsApp y ticket como imagen; Gasto/Corte/Productos ya estaban en el menú; Cerrar turno, Atajos (solo PC) y Pantalla completa pasan al menú (sección ACCIONES); el Cajero ve en Ventas solo Poner en espera y Reimprimir */ /* v87: Entradas — corregir una entrada sin borrarla (ajuste por delta en su almacén, con guarda de stock negativo) + folio de factura opcional (se guarda y se ve en la lista) · el selector de almacén ya existía desde Multi-almacén */ /* v86: modo día (fondo blanco) / modo noche (fondo negro) con botón ☀️/🌙 en el encabezado, se guarda por aparato (kupoPosV146Tema) */ /* v85: descuentos opcionales por negocio (interruptor en Opciones de esta licencia, solo Administrador, % o $ al cobrar, sale en el ticket) · v84: revisión periódica de Mensajes en segundo plano + ticket como imagen (canvas PNG para WhatsApp) · v83: casilla "Abrir la ventana de imprimir al cobrar" en el móvil (Personalizar ticket, apagada por default) · v82: chat interno "Mensajes" (solo texto, Admin <-> Cajeros, Realtime + polling 20 s) · v81: botón Sincronizar en el encabezado (siempre visible, con insignia de pendientes) · v80: botón Enviar ticket por WhatsApp (wa.me) + texto plano del ticket · v79: instalación resistente — cada archivo se guarda por separado y si uno falla (internet lento) el service worker igual se instala, para que Chrome/Edge ofrezcan instalar la app */ /* v75: licencia ya no se prellena (campo vacío al abrir la pantalla) + iconos PNG 192/512 en el manifiesto y en caché para que Chrome ofrezca instalar la app */
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
