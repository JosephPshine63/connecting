import { Injectable, signal } from '@angular/core';

export type ChatBackgroundId = 'none' | 'birds-solid' | 'birds-red' | 'birds-outline';

const STORAGE_KEY = 'chatBackground';
const DEFAULT_BACKGROUND: ChatBackgroundId = 'none';
const VALID_BACKGROUNDS: ChatBackgroundId[] = ['none', 'birds-solid', 'birds-red', 'birds-outline'];

@Injectable({ providedIn: 'root' })
export class ChatBackgroundService {

  readonly background = signal<ChatBackgroundId>(this.readInitial());

  constructor() {
    this.applyToDom(this.background());
  }

  setBackground(id: ChatBackgroundId): void {
    this.background.set(id);
    localStorage.setItem(STORAGE_KEY, id);
    this.applyToDom(id);
  }

  private applyToDom(id: ChatBackgroundId): void {
    document.documentElement.setAttribute('data-chat-bg', id);
  }

  private readInitial(): ChatBackgroundId {
    const stored = localStorage.getItem(STORAGE_KEY) as ChatBackgroundId | null;
    return stored && VALID_BACKGROUNDS.includes(stored) ? stored : DEFAULT_BACKGROUND;
  }
}
