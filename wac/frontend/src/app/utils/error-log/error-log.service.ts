import { Injectable, signal } from '@angular/core';

export interface AppErrorEntry {
  id: number;
  timestamp: Date;
  source: 'http' | 'client';
  message: string;
  status?: number;
  url?: string;
}

const MAX_ENTRIES = 50;

let nextId = 0;

/**
 * In-memory only (no localStorage) — this is a live-debugging aid for the current
 * session, not a persisted audit log; it resets on reload like the browser console does.
 */
@Injectable({ providedIn: 'root' })
export class ErrorLogService {

  readonly errors = signal<AppErrorEntry[]>([]);
  readonly unseenCount = signal(0);

  report(entry: Omit<AppErrorEntry, 'id' | 'timestamp'>): void {
    this.errors.update(list => [{ ...entry, id: nextId++, timestamp: new Date() }, ...list].slice(0, MAX_ENTRIES));
    this.unseenCount.update(n => n + 1);
  }

  markSeen(): void {
    this.unseenCount.set(0);
  }

  clear(): void {
    this.errors.set([]);
    this.unseenCount.set(0);
  }
}
