import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { DatePipe } from '@angular/common';
import { UserService } from '../../services/services/user.service';
import { UserResponse } from '../../services/models/user-response';

@Component({
  selector: 'app-user-card',
  templateUrl: './user-card.component.html',
  styleUrl: './user-card.component.scss',
  imports: [DatePipe]
})
export class UserCardComponent implements OnChanges {

  @Input() userId: string | null = null;
  @Output() closed = new EventEmitter<void>();

  user: UserResponse | null = null;
  loading = false;

  constructor(private userService: UserService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['userId'] && this.userId) {
      this.loading = true;
      this.user = null;
      this.userService.getUserById({ id: this.userId }).subscribe({
        next: user => { this.user = user; this.loading = false; },
        error: () => { this.loading = false; }
      });
    }
  }

  close(): void {
    this.closed.emit();
  }

  displayName(): string {
    if (!this.user) return '';
    return [this.user.firstName, this.user.lastName].filter(Boolean).join(' ');
  }

  initial(): string {
    const source = this.user?.username || this.user?.firstName || '?';
    return source.charAt(0).toUpperCase();
  }
}
