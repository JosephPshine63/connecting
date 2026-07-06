import { Injectable, signal } from '@angular/core';

const MUTE_STORAGE_KEY = 'mutedChats';

@Injectable({ providedIn: 'root' })
export class MuteService {

  readonly mutedChatIds = signal<Set<string>>(this.readInitial());

  isMuted(chatId: string | undefined): boolean {
    return !!chatId && this.mutedChatIds().has(chatId);
  }

  toggleMute(chatId: string): void {
    const current = new Set(this.mutedChatIds());
    if (current.has(chatId)) {
      current.delete(chatId);
    } else {
      current.add(chatId);
    }
    this.mutedChatIds.set(current);
    localStorage.setItem(MUTE_STORAGE_KEY, JSON.stringify([...current]));
  }

  private readInitial(): Set<string> {
    try {
      const stored = localStorage.getItem(MUTE_STORAGE_KEY);
      return stored ? new Set(JSON.parse(stored)) : new Set();
    } catch {
      return new Set();
    }
  }
}
