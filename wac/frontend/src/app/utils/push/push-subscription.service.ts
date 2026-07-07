import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

// Hand-written, mirroring utils/username/username.service.ts — not part of the
// ng-openapi-gen pipeline. keycloakHttpInterceptor already attaches the Bearer token to
// every HttpClient request regardless of target path, so no extra auth wiring is needed.
@Injectable({ providedIn: 'root' })
export class PushSubscriptionService {

  constructor(private http: HttpClient) {}

  isSupported(): boolean {
    return 'serviceWorker' in navigator && 'PushManager' in window;
  }

  /** iOS Safari only exposes PushManager to a page running in standalone display mode
   *  (launched from a Home-Screen icon via "Add to Home Screen") — a regular Safari tab
   *  can't subscribe at all. Not treated as an error, just an early exit. */
  isIosNonStandalone(): boolean {
    const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent);
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches
      || (navigator as unknown as { standalone?: boolean }).standalone === true;
    return isIos && !isStandalone;
  }

  async registerServiceWorkerAndSubscribe(): Promise<void> {
    if (!this.isSupported() || this.isIosNonStandalone()) {
      return;
    }
    try {
      const registration = await navigator.serviceWorker.register('/sw.js');
      let subscription = await registration.pushManager.getSubscription();
      if (!subscription) {
        const { publicKey } = await firstValueFrom(
          this.http.get<{ publicKey: string }>('/api/v1/push/vapid-public-key')
        );
        if (!publicKey) {
          return; // VAPID keys not configured server-side yet — Web Push is disabled
        }
        subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: this.urlBase64ToUint8Array(publicKey)
        });
      }
      const json = subscription.toJSON();
      await firstValueFrom(this.http.post<void>('/api/v1/users/me/push-subscriptions', {
        endpoint: json.endpoint,
        p256dh: json.keys?.['p256dh'],
        auth: json.keys?.['auth']
      }));
    } catch (err) {
      console.error('[push] service worker registration/subscription failed', err);
    }
  }

  async unsubscribe(): Promise<void> {
    if (!this.isSupported()) return;
    try {
      const registration = await navigator.serviceWorker.getRegistration();
      const subscription = await registration?.pushManager.getSubscription();
      if (!subscription) return;
      const endpoint = subscription.endpoint;
      await subscription.unsubscribe();
      await firstValueFrom(this.http.delete<void>('/api/v1/users/me/push-subscriptions', { body: { endpoint } }));
    } catch {
      // best-effort: proceed with logout even if this fails
    }
  }

  private urlBase64ToUint8Array(base64String: string): Uint8Array {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
    const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
    const rawData = window.atob(base64);
    const outputArray = new Uint8Array(rawData.length);
    for (let i = 0; i < rawData.length; ++i) {
      outputArray[i] = rawData.charCodeAt(i);
    }
    return outputArray;
  }
}
