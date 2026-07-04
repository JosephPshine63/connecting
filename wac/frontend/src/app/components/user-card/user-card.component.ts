import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { DatePipe } from '@angular/common';
import { UserService } from '../../services/services/user.service';
import { UserResponse } from '../../services/models/user-response';
import { ChatService } from '../../services/services/chat.service';
import { ChatResponse } from '../../services/models/chat-response';
import { KeycloakService } from '../../utils/keycloak/keycloak.service';

@Component({
  selector: 'app-user-card',
  templateUrl: './user-card.component.html',
  styleUrl: './user-card.component.scss',
  imports: [DatePipe]
})
export class UserCardComponent implements OnChanges {

  @Input() userId: string | null = null;
  @Output() closed = new EventEmitter<void>();
  @Output() chatRequested = new EventEmitter<ChatResponse>();

  user: UserResponse | null = null;
  loading = false;
  requestSent = false;

  constructor(
    private userService: UserService,
    private chatService: ChatService,
    private keycloakService: KeycloakService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['userId'] && this.userId) {
      this.loading = true;
      this.user = null;
      this.requestSent = false;
      this.userService.getUserById({ id: this.userId }).subscribe({
        next: user => { this.user = user; this.loading = false; },
        error: () => { this.loading = false; }
      });
    }
  }

  close(): void {
    this.closed.emit();
  }

  sendChatRequest(): void {
    if (!this.user?.id || this.requestSent) return;
    this.chatService.createChat({ 'receiver-id': this.user.id }).subscribe({
      next: (res) => {
        this.requestSent = true;
        const chat: ChatResponse = {
          id: res.response,
          name: this.user!.username ? '@' + this.user!.username : this.displayName(),
          senderId: this.keycloakService.userId,
          receiverId: this.user!.id,
          avatarUrl: this.user!.avatarUrl,
          status: 'PENDING',
          pendingMessageCount: 0
        };
        this.chatRequested.emit(chat);
      }
    });
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
