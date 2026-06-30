import { Injectable } from '@angular/core';

const APP_ICON = '/wacchat-logo.png';

@Injectable({ providedIn: 'root' })
export class BrowserNotificationService {

  requestPermission(): void {
    if (!('Notification' in window)) return;
    if (Notification.permission === 'default') {
      Notification.requestPermission();
    }
  }

  notify(title: string, body: string, onClick?: () => void): void {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    const notification = new Notification(title, { body, icon: APP_ICON });
    notification.onclick = () => {
      window.focus();
      onClick?.();
      notification.close();
    };
  }
}
