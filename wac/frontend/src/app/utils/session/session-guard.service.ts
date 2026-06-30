import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class SessionGuardService {

  readonly blocked = signal(false);

  markBlocked(): void {
    this.blocked.set(true);
  }

  markUnblocked(): void {
    this.blocked.set(false);
  }
}
