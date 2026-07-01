import {AfterViewChecked, Component, ElementRef, NgZone, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {ChatListComponent} from '../../components/chat-list/chat-list.component';
import {KeycloakService} from '../../utils/keycloak/keycloak.service';
import {ChatResponse} from '../../services/models/chat-response';
import {DatePipe} from '@angular/common';
import {MessageService} from '../../services/services/message.service';
import {MessageResponse} from '../../services/models/message-response';
import {UserResponse} from '../../services/models/user-response';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import {FormsModule} from '@angular/forms';
import {MessageRequest} from '../../services/models/message-request';
import {Notification} from './models/notification';
import {ChatService} from '../../services/services/chat.service';
import {PickerComponent} from '@ctrl/ngx-emoji-mart';
import {EmojiData} from '@ctrl/ngx-emoji-mart/ngx-emoji';
import {UsernameSetupComponent} from '../../components/username-setup/username-setup.component';
import {UsernameService} from '../../utils/username/username.service';
import {UserCardComponent} from '../../components/user-card/user-card.component';
import {AvatarUploadComponent} from '../../components/avatar-upload/avatar-upload.component';
import {SessionBlockedComponent} from '../../components/session-blocked/session-blocked.component';
import {SessionGuardService} from '../../utils/session/session-guard.service';
import {BrowserNotificationService} from '../../utils/notifications/browser-notification.service';

const HEARTBEAT_INTERVAL_MS = 60000;

@Component({
  selector: 'app-main',
  imports: [
    ChatListComponent,
    DatePipe,
    FormsModule,
    PickerComponent,
    UsernameSetupComponent,
    UserCardComponent,
    AvatarUploadComponent,
    SessionBlockedComponent
  ],
  templateUrl: './main.component.html',
  styleUrl: './main.component.scss'
})
export class MainComponent implements OnInit, OnDestroy, AfterViewChecked {

  selectedChat: ChatResponse = {};
  chats: Array<ChatResponse> = [];
  chatMessages: Array<MessageResponse> = [];
  socketClient: Client | null = null;
  messageContent: string = '';
  showEmojis = false;
  showDemoBanner = !sessionStorage.getItem('demoBannerDismissed');
  ageVerified = !!localStorage.getItem('ageVerified');
  ageDenied = false;
  currentUser: UserResponse | null = null;
  showUsernameModal = false;
  selectedCardUserId: string | null = null;
  showAvatarUpload = false;
  @ViewChild('scrollableDiv') scrollableDiv!: ElementRef<HTMLDivElement>;
  private notificationSubscription: StompSubscription | null = null;
  private heartbeatHandle: ReturnType<typeof setInterval> | null = null;

  constructor(
    private chatService: ChatService,
    private messageService: MessageService,
    private keycloakService: KeycloakService,
    private usernameService: UsernameService,
    private ngZone: NgZone,
    protected sessionGuard: SessionGuardService,
    private browserNotifications: BrowserNotificationService,
  ) {
  }

  ngAfterViewChecked(): void {
    this.scrollToBottom();
  }

  ngOnDestroy(): void {
    this.notificationSubscription?.unsubscribe();
    if (this.socketClient !== null) {
      this.socketClient.deactivate();
      this.socketClient = null;
    }
    if (this.heartbeatHandle !== null) {
      clearInterval(this.heartbeatHandle);
    }
  }

  ngOnInit(): void {
    this.browserNotifications.requestPermission();
    this.initWebSocket();
    this.getAllChats();
    this.refreshCurrentUser();
    this.heartbeatHandle = setInterval(() => this.refreshCurrentUser(), HEARTBEAT_INTERVAL_MS);
  }

  private refreshCurrentUser(): void {
    this.usernameService.getMe().subscribe({
      next: user => {
        this.currentUser = user;
        if (!user.username) {
          this.showUsernameModal = true;
        }
      },
      error: err => console.error('Failed to refresh current user', err)
    });
  }

  onUsernameSet(username: string): void {
    this.showUsernameModal = false;
    if (this.currentUser) {
      this.currentUser = { ...this.currentUser, username };
    }
  }

  openUserCard(userId: string): void {
    this.selectedCardUserId = userId;
  }

  closeUserCard(): void {
    this.selectedCardUserId = null;
  }

  onAvatarChanged(avatarUrl: string | undefined): void {
    if (this.currentUser) {
      this.currentUser = { ...this.currentUser, avatarUrl };
    }
    this.showAvatarUpload = false;
  }

  chatSelected(chatResponse: ChatResponse) {
    if (!this.chats.find(c => c.id === chatResponse.id)) {
      this.chats.unshift(chatResponse);
    }
    this.selectedChat = chatResponse;
    this.getAllChatMessages(chatResponse.id as string);
    this.setMessagesToSeen();
    this.selectedChat.unreadCount = 0;
  }

  isSelfMessage(message: MessageResponse): boolean {
    return message.senderId === this.keycloakService.userId;
  }

  sendMessage() {
    if (this.messageContent) {
      const messageRequest: MessageRequest = {
        chatId: this.selectedChat.id as string,
        content: this.messageContent,
        type: 'TEXT',
      };
      this.messageService.saveMessage({
        body: messageRequest
      }).subscribe({
        next: () => {
          const message: MessageResponse = {
            senderId: this.getSenderId(),
            receiverId: this.getReceiverId(),
            content: this.messageContent,
            type: 'TEXT',
            state: 'SENT',
            createdAt: new Date().toString()
          };
          this.selectedChat.lastMessage = this.messageContent;
          this.chatMessages.push(message);
          this.messageContent = '';
          this.showEmojis = false;
        }
      });
    }
  }

  keyDown(event: KeyboardEvent) {
    if (event.key === 'Enter') {
      this.sendMessage();
    }
  }

  onSelectEmojis(emojiSelected: any) {
    const emoji: EmojiData = emojiSelected.emoji;
    this.messageContent += emoji.native;
  }

  onClick() {
    this.setMessagesToSeen();
  }

  mediaSrc(media: string): string {
    return media.startsWith('http') ? media : 'data:image/jpg;base64,' + media;
  }

  uploadMedia(target: EventTarget | null) {
    const file = this.extractFileFromTarget(target);
    if (file !== null) {
      const reader = new FileReader();
      reader.onload = () => {
        if (reader.result) {

          const mediaLines = reader.result.toString().split(',')[1];

          this.messageService.uploadMedia({
            'chat-id': this.selectedChat.id as string,
            body: {
              file: file
            }
          }).subscribe({
            next: () => {
              const message: MessageResponse = {
                senderId: this.getSenderId(),
                receiverId: this.getReceiverId(),
                content: 'Attachment',
                type: 'IMAGE',
                state: 'SENT',
                media: [mediaLines],
                createdAt: new Date().toString()
              };
              this.chatMessages.push(message);
            }
          });
        }
      }
      reader.readAsDataURL(file);
    }
  }

  confirmAge() {
    localStorage.setItem('ageVerified', '1');
    this.ageVerified = true;
  }

  denyAge() {
    this.ageDenied = true;
  }

  dismissBanner() {
    sessionStorage.setItem('demoBannerDismissed', '1');
    this.showDemoBanner = false;
  }

  logout() {
    this.keycloakService.logout();
  }

  userProfile() {
    this.keycloakService.accountManagement();
  }

  private setMessagesToSeen() {
    if (!this.selectedChat.id) return;
    this.messageService.setMessageToSeen({
      'chat-id': this.selectedChat.id as string
    }).subscribe({
      next: () => {
      }
    });
  }

  private getAllChats() {
    this.chatService.getChatsByReceiver()
      .subscribe({
        next: (res) => {
          this.chats = res;
        }
      });
  }

  private getAllChatMessages(chatId: string) {
    this.messageService.getAllMessages({
      'chat-id': chatId
    }).subscribe({
      next: (messages) => {
        this.chatMessages = messages;
      }
    });
  }

  private initWebSocket() {
    if (!this.keycloakService.keycloak.tokenParsed?.sub) return;
    const subUrl = `/user/${this.keycloakService.keycloak.tokenParsed.sub}/chat`;
    this.socketClient = new Client({
      webSocketFactory: () => new SockJS(`${window.location.origin}/ws`) as any,
      connectHeaders: {
        'Authorization': 'Bearer ' + this.keycloakService.keycloak.token
      },
      onConnect: () => {
        this.notificationSubscription = this.socketClient!.subscribe(
          subUrl,
          (message: IMessage) => {
            const notification: Notification = JSON.parse(message.body);
            this.handleNotification(notification);
          }
        );
      },
      onStompError: (frame) => console.error('WebSocket error:', frame)
    });
    this.socketClient.activate();
  }

  private handleNotification(notification: Notification) {
    if (!notification) return;
    this.ngZone.run(() => {
      if (notification.type === 'AVATAR_UPDATED') {
        this.applyAvatarUpdate(notification);
        return;
      }
      if (notification.type === 'MESSAGE' || notification.type === 'IMAGE') {
        this.maybeShowDesktopNotification(notification);
      }
      if (this.selectedChat && this.selectedChat.id === notification.chatId) {
        switch (notification.type) {
          case 'MESSAGE':
          case 'IMAGE':
            const message: MessageResponse = {
              senderId: notification.senderId,
              receiverId: notification.receiverId,
              content: notification.content,
              type: notification.messageType,
              media: notification.media,
              createdAt: new Date().toString()
            };
            if (notification.type === 'IMAGE') {
              this.selectedChat.lastMessage = 'Attachment';
            } else {
              this.selectedChat.lastMessage = notification.content;
            }
            this.chatMessages.push(message);
            break;
          case 'SEEN':
            this.chatMessages.forEach(m => m.state = 'SEEN');
            break;
        }
      } else {
        const destChat = this.chats.find(c => c.id === notification.chatId);
        if (destChat && notification.type !== 'SEEN') {
          if (notification.type === 'MESSAGE') {
            destChat.lastMessage = notification.content;
          } else if (notification.type === 'IMAGE') {
            destChat.lastMessage = 'Attachment';
          }
          destChat.lastMessageTime = new Date().toString();
          destChat.unreadCount! += 1;
        } else if (notification.type === 'MESSAGE') {
          const newChat: ChatResponse = {
            id: notification.chatId,
            senderId: notification.senderId,
            receiverId: notification.receiverId,
            lastMessage: notification.content,
            name: notification.chatName,
            unreadCount: 1,
            lastMessageTime: new Date().toString()
          };
          this.chats.unshift(newChat);
        }
      }
    });
  }

  private maybeShowDesktopNotification(notification: Notification): void {
    const chatIsOpenAndFocused = document.hasFocus() && this.selectedChat?.id === notification.chatId;
    if (chatIsOpenAndFocused) return;

    const title = notification.chatName || 'Nuovo messaggio';
    const body = notification.type === 'IMAGE' ? 'Ti ha inviato un allegato' : (notification.content || '');
    const chatId = notification.chatId;
    this.browserNotifications.notify(title, body, () => {
      this.ngZone.run(() => {
        const chat = this.chats.find(c => c.id === chatId);
        if (chat) {
          this.chatSelected(chat);
        }
      });
    });
  }

  private applyAvatarUpdate(notification: Notification): void {
    const partnerId = notification.senderId;
    if (!partnerId) return;
    if (this.selectedChat && (this.selectedChat.senderId === partnerId || this.selectedChat.receiverId === partnerId)) {
      this.selectedChat.avatarUrl = notification.avatarUrl;
    }
    const destChat = this.chats.find(c => c.senderId === partnerId || c.receiverId === partnerId);
    if (destChat) {
      destChat.avatarUrl = notification.avatarUrl;
    }
  }

  private getSenderId(): string {
    if (this.selectedChat.senderId === this.keycloakService.userId) {
      return this.selectedChat.senderId as string;
    }
    return this.selectedChat.receiverId as string;
  }

  getReceiverId(): string {
    if (this.selectedChat.senderId === this.keycloakService.userId) {
      return this.selectedChat.receiverId as string;
    }
    return this.selectedChat.senderId as string;
  }

  private scrollToBottom() {
    if (this.scrollableDiv) {
      const div = this.scrollableDiv.nativeElement;
      div.scrollTop = div.scrollHeight;
    }
  }

  private extractFileFromTarget(target: EventTarget | null): File | null {
    const htmlInputTarget = target as HTMLInputElement;
    if (target === null || htmlInputTarget.files === null) {
      return null;
    }
    return htmlInputTarget.files[0];
  }
}
