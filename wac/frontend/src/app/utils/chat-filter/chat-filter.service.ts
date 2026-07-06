import { Injectable, signal } from '@angular/core';

export type ChatFilter = 'all' | 'unread' | 'favorites' | 'blocked' | 'archived';

const CHAT_FILTER_STORAGE_KEY = 'activeChatFilter';
const DEFAULT_FILTER: ChatFilter = 'all';
const VALID_FILTERS: ChatFilter[] = ['all', 'unread', 'favorites', 'blocked', 'archived'];

@Injectable({ providedIn: 'root' })
export class ChatFilterService {

  readonly filter = signal<ChatFilter>(this.readInitial());

  setFilter(filter: ChatFilter): void {
    this.filter.set(filter);
    localStorage.setItem(CHAT_FILTER_STORAGE_KEY, filter);
  }

  private readInitial(): ChatFilter {
    const stored = localStorage.getItem(CHAT_FILTER_STORAGE_KEY) as ChatFilter | null;
    return stored && VALID_FILTERS.includes(stored) ? stored : DEFAULT_FILTER;
  }
}
