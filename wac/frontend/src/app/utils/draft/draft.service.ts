import { Injectable, signal } from '@angular/core';
import { KeycloakService } from '../keycloak/keycloak.service';

@Injectable({ providedIn: 'root' })
export class DraftService {

  readonly drafts = signal<Record<string, string>>(this.readInitial());

  constructor(private keycloakService: KeycloakService) {
  }

  // Namespaced per user — localStorage is shared by every account that logs into the
  // same browser, so a global key would leak one user's drafts to the next to log in.
  private get storageKey(): string {
    return `chatDrafts:${this.keycloakService.userId}`;
  }

  getDraft(chatId: string): string {
    return this.drafts()[chatId] ?? '';
  }

  setDraft(chatId: string, text: string): void {
    const current = { ...this.drafts() };
    if (text) {
      current[chatId] = text;
    } else {
      delete current[chatId];
    }
    this.drafts.set(current);
    localStorage.setItem(this.storageKey, JSON.stringify(current));
  }

  clearDraft(chatId: string): void {
    this.setDraft(chatId, '');
  }

  clearAll(): void {
    localStorage.removeItem(this.storageKey);
    this.drafts.set({});
  }

  private readInitial(): Record<string, string> {
    try {
      const stored = localStorage.getItem(this.storageKey);
      return stored ? JSON.parse(stored) : {};
    } catch {
      return {};
    }
  }
}
