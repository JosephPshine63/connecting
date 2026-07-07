// Hand-written (not @angular/service-worker) — served at the site root from public/sw.js
// by the Angular build's static-asset globbing (same mechanism as favicon.png/theme-init.js),
// giving it site-wide scope for PushManager subscriptions.

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('push', (event) => {
  if (!event.data) return;
  const payload = event.data.json();
  const title = payload.title || 'Pio';
  const options = {
    body: payload.body || '',
    icon: '/favicon.png',
    badge: '/favicon.png',
    data: { url: payload.url || '/', chatId: payload.chatId || null }
  };

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      // Coarser than main.component.ts's per-chat check (chatIsOpenAndFocused) — matches
      // the existing !document.hasFocus()-only precedent used for the incoming-call banner.
      // Exact per-chat parity would need a postMessage bridge; deferred to v1.1.
      const hasFocusedClient = clientList.some((client) => client.focused);
      if (hasFocusedClient) {
        return undefined;
      }
      return self.registration.showNotification(title, options);
    })
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = event.notification.data && event.notification.data.url ? event.notification.data.url : '/';

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ('focus' in client) {
          return client.focus();
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(url);
      }
      return undefined;
    })
  );
});
