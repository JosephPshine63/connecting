import { Injectable, signal } from '@angular/core';

const DRAFT_STORAGE_KEY = 'chatDrafts';

@Injectable({ providedIn: 'root' })
export class DraftService {

  readonly drafts = signal<Record<string, string>>(this.readInitial());

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
    localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(current));
  }

  clearDraft(chatId: string): void {
    this.setDraft(chatId, '');
  }

  private readInitial(): Record<string, string> {
    try {
      const stored = localStorage.getItem(DRAFT_STORAGE_KEY);
      return stored ? JSON.parse(stored) : {};
    } catch {
      return {};
    }
  }
}
