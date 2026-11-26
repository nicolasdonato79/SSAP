// Service worker de la app: guarda los archivos de la app para que abra sin conexión.
// IMPORTANTE: al cambiar cualquier archivo de /app, subir VERSION (así los equipos bajan la versión nueva).
const VERSION = 'ssap-app-1';
const ARCHIVOS = [
  './', 'index.html', 'app.css', 'manifest.webmanifest',
  'js/main.js', 'js/db.js', 'js/db-worker.js', 'js/api.js', 'js/sync.js', 'js/local.js', 'js/excel.js',
  'js/marca.js', 'js/busqueda.js', 'js/texto.js',
  'vendor/preact-htm.js', 'vendor/read-excel-file.js', 'vendor/sqlite/sqlite3.js', 'vendor/sqlite/sqlite3.wasm',
  'img/icono.svg', 'img/icono-192.png', 'img/icono-512.png', 'img/icono-maskable-512.png', 'img/apple-touch-icon.png',
];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then(c => c.addAll(ARCHIVOS.map(a => new Request(a, { cache: 'reload' })))));
});

self.addEventListener('activate', (e) => {
  e.waitUntil((async () => {
    for (const k of await caches.keys()) if (k !== VERSION) await caches.delete(k);
    await self.clients.claim();
  })());
});

self.addEventListener('message', (e) => {
  if (e.data === 'SKIP_WAITING') self.skipWaiting();
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  // La API nunca se guarda: siempre va al servidor (y si no hay conexión, falla y la app lo maneja)
  if (e.request.method !== 'GET' || url.origin !== location.origin || !url.pathname.startsWith('/app/')) return;
  e.respondWith((async () => {
    const cache = await caches.open(VERSION);
    // Abrir la app (con cualquier #/ruta): la página guardada
    const pedido = e.request.mode === 'navigate' ? 'index.html' : e.request;
    const guardado = await cache.match(pedido, { ignoreSearch: true });
    if (guardado) return guardado;
    try {
      return await fetch(e.request);
    } catch {
      return new Response('Sin conexión', { status: 503 });
    }
  })());
});
