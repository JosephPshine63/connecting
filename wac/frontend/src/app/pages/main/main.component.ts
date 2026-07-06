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
import {CallSignal} from './models/call-signal';
import {ChatService} from '../../services/services/chat.service';
import {CallApiService} from '../../utils/call/call-api.service';
import {WebRtcCallService} from '../../utils/webrtc/webrtc-call.service';
import {CallComponent} from '../../components/call/call.component';
import {PickerComponent} from '@ctrl/ngx-emoji-mart';
import {EmojiData} from '@ctrl/ngx-emoji-mart/ngx-emoji';
import {UsernameSetupComponent} from '../../components/username-setup/username-setup.component';
import {UsernameService} from '../../utils/username/username.service';
import {UserCardComponent} from '../../components/user-card/user-card.component';
import {AvatarUploadComponent} from '../../components/avatar-upload/avatar-upload.component';
import {SessionBlockedComponent} from '../../components/session-blocked/session-blocked.component';
import {SessionGuardService} from '../../utils/session/session-guard.service';
import {BrowserNotificationService} from '../../utils/notifications/browser-notification.service';
import {SettingsComponent} from '../../components/settings/settings.component';
import {MediaLightboxComponent} from '../../components/media-lightbox/media-lightbox.component';
import {DraftService} from '../../utils/draft/draft.service';
import {MuteService} from '../../utils/mute/mute.service';
import {MessageActionsMenuComponent} from '../../components/message-actions-menu/message-actions-menu.component';
import {ReplyPreviewBarComponent} from '../../components/reply-preview-bar/reply-preview-bar.component';
import {ForwardPickerComponent} from '../../components/forward-picker/forward-picker.component';

const HEARTBEAT_INTERVAL_MS = 60000;
const ARNO_USER_ID = '00000000-0000-0000-0000-000000000001';
const ARNO_TYPING_TIMEOUT_MS = 20000;
const PEER_TYPING_SAFETY_TIMEOUT_MS = 8000;
const TYPING_STOP_DEBOUNCE_MS = 2000;
const TYPING_SEND_THROTTLE_MS = 3000;
const SCROLL_THRESHOLD_PX = 100;
// Must match ChatConstants.MAX_PENDING_MESSAGES in the backend.
const MAX_PENDING_MESSAGES = 3;

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
    SessionBlockedComponent,
    SettingsComponent,
    CallComponent,
    MediaLightboxComponent,
    MessageActionsMenuComponent,
    ReplyPreviewBarComponent,
    ForwardPickerComponent
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
  showSettings = false;
  @ViewChild('scrollableDiv') scrollableDiv!: ElementRef<HTMLDivElement>;
  private notificationSubscription: StompSubscription | null = null;
  private heartbeatHandle: ReturnType<typeof setInterval> | null = null;
  peerTypingChatId: string | null = null;
  private peerTypingTimeout: ReturnType<typeof setTimeout> | null = null;
  private typingStopTimeout: ReturnType<typeof setTimeout> | null = null;
  private typingActive = false;
  private lastTypingSentAt = 0;

  recordingState: 'idle' | 'recording' | 'preview' | 'sending' = 'idle';
  recordingElapsedSeconds = 0;
  recordedPreviewUrl: string | null = null;
  private static readonly MAX_RECORDING_SECONDS = 300;
  private mediaRecorder: MediaRecorder | null = null;
  private recordedChunks: Blob[] = [];
  private recordedBlob: Blob | null = null;
  private recordingTimerHandle: ReturnType<typeof setInterval> | null = null;

  lightboxMedia: { url: string; type: 'IMAGE' | 'VIDEO' } | null = null;

  activeMessageMenuId: number | null = null;
  replyingToMessage: MessageResponse | null = null;
  forwardingMessage: MessageResponse | null = null;
  editingMessage: MessageResponse | null = null;
  editContent = '';
  reactingToMessageId: number | null = null;

  showMessageSearch = false;
  messageSearchQuery = '';
  searchMatchIndices: number[] = [];
  currentSearchMatchIndex = -1;

  isScrolledUp = false;
  private forceScrollOnNextCheck = false;

  callState: 'idle' | 'incoming' | 'outgoing' | 'in-call' = 'idle';
  activeCallChatId: string | null = null;
  activeCallPeerId: string | null = null;
  activeCallPeerName: string | null = null;
  activeCallPeerAvatarUrl: string | null = null;
  activeCallType: 'AUDIO' | 'VIDEO' = 'AUDIO';
  private pendingOfferSdp: string | null = null;
  private callSignalSubscription: StompSubscription | null = null;

  constructor(
    private chatService: ChatService,
    private messageService: MessageService,
    private keycloakService: KeycloakService,
    private usernameService: UsernameService,
    private ngZone: NgZone,
    protected sessionGuard: SessionGuardService,
    private browserNotifications: BrowserNotificationService,
    private callApiService: CallApiService,
    private webRtcCallService: WebRtcCallService,
    private draftService: DraftService,
    private muteService: MuteService,
  ) {
  }

  ngAfterViewChecked(): void {
    this.scrollToBottom();
  }

  ngOnDestroy(): void {
    this.notificationSubscription?.unsubscribe();
    this.callSignalSubscription?.unsubscribe();
    this.webRtcCallService.close();
    if (this.socketClient !== null) {
      this.socketClient.deactivate();
      this.socketClient = null;
    }
    if (this.heartbeatHandle !== null) {
      clearInterval(this.heartbeatHandle);
    }
    if (this.peerTypingTimeout !== null) {
      clearTimeout(this.peerTypingTimeout);
    }
    if (this.typingStopTimeout !== null) {
      clearTimeout(this.typingStopTimeout);
    }
    if (this.recordingTimerHandle !== null) {
      clearInterval(this.recordingTimerHandle);
    }
    if (this.mediaRecorder && this.mediaRecorder.state !== 'inactive') {
      this.mediaRecorder.stream?.getTracks().forEach(t => t.stop());
    }
    window.removeEventListener('pagehide', this.releaseSessionOnUnload);
  }

  ngOnInit(): void {
    this.browserNotifications.requestPermission();
    this.initWebSocket();
    this.getAllChats();
    this.refreshCurrentUser();
    this.heartbeatHandle = setInterval(() => this.refreshCurrentUser(), HEARTBEAT_INTERVAL_MS);
    window.addEventListener('pagehide', this.releaseSessionOnUnload);
    // RTCPeerConnection's onicecandidate fires outside Angular's zone, same as STOMP
    // callbacks below — wrap in ngZone.run so any state it touches gets change-detected.
    this.webRtcCallService.localIceCandidate$.subscribe(candidate => {
      this.ngZone.run(() => {
        if (!this.activeCallChatId) return;
        this.callApiService.iceCandidate(
          this.activeCallChatId, candidate.candidate, candidate.sdpMid, candidate.sdpMLineIndex
        ).subscribe();
      });
    });
  }

  // Releases the single-session lock when the tab closes/navigates away, so reopening the app
  // right after doesn't get falsely blocked as a "conflicting" session for the stale-after
  // window. Uses fetch+keepalive (not HttpClient) since regular requests can be aborted mid-flight
  // once the page starts unloading; keepalive lets this one survive that.
  private releaseSessionOnUnload = (): void => {
    const token = this.keycloakService.keycloak.token;
    if (!token) return;
    fetch('/api/v1/users/me/session', {
      method: 'DELETE',
      headers: {
        Authorization: `Bearer ${token}`,
        'X-Tab-Id': this.keycloakService.tabId
      },
      keepalive: true
    }).catch(() => {
      // best-effort: the tab is closing, nothing to recover from here
    });
  };

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

  onChatAccepted(chat: ChatResponse): void {
    if (this.selectedChat.id === chat.id) {
      this.selectedChat.status = 'ACCEPTED';
    }
  }

  onChatRejected(chat: ChatResponse): void {
    this.chats = this.chats.filter(c => c.id !== chat.id);
    if (this.selectedChat.id === chat.id) {
      this.selectedChat = {};
    }
  }

  onChatRequested(chat: ChatResponse): void {
    if (!this.chats.find(c => c.id === chat.id)) {
      this.chats.unshift(chat);
    }
    this.closeUserCard();
  }

  closeUserCard(): void {
    this.selectedCardUserId = null;
  }

  onUserBlocked(blockedUserId: string): void {
    const blockedChat = this.chats.find(c => c.senderId === blockedUserId || c.receiverId === blockedUserId);
    this.chats = this.chats.filter(c => c.senderId !== blockedUserId && c.receiverId !== blockedUserId);
    if (blockedChat && this.selectedChat.id === blockedChat.id) {
      this.selectedChat = {};
    }
  }

  onAvatarChanged(avatarUrl: string | undefined): void {
    if (this.currentUser) {
      this.currentUser = { ...this.currentUser, avatarUrl };
    }
    this.showAvatarUpload = false;
  }

  chatSelected(chatResponse: ChatResponse) {
    this.sendTypingStop();
    this.stopPeerTyping();
    if (!this.chats.find(c => c.id === chatResponse.id)) {
      this.chats.unshift(chatResponse);
    }
    this.showMessageSearch = false;
    this.messageSearchQuery = '';
    this.searchMatchIndices = [];
    this.currentSearchMatchIndex = -1;
    this.activeMessageMenuId = null;
    this.replyingToMessage = null;
    this.editingMessage = null;
    this.reactingToMessageId = null;
    if (this.selectedChat.id) {
      this.draftService.setDraft(this.selectedChat.id, this.messageContent);
    }
    this.selectedChat = chatResponse;
    this.messageContent = this.draftService.getDraft(chatResponse.id as string);
    this.isScrolledUp = false;
    this.forceScrollOnNextCheck = true;
    this.getAllChatMessages(chatResponse.id as string);
    this.setMessagesToSeen();
    this.selectedChat.unreadCount = 0;
  }

  isSelfMessage(message: MessageResponse): boolean {
    return message.senderId === this.keycloakService.userId;
  }

  sendMessage() {
    if (this.messageContent) {
      const replyToId = this.replyingToMessage?.id;
      const messageRequest: MessageRequest = {
        chatId: this.selectedChat.id as string,
        content: this.messageContent,
        type: 'TEXT',
        replyToId
      };
      this.messageService.saveMessage({
        body: messageRequest
      }).subscribe({
        next: (response) => {
          const message: MessageResponse = {
            id: response.id,
            senderId: this.getSenderId(),
            receiverId: this.getReceiverId(),
            content: this.messageContent,
            type: 'TEXT',
            state: 'SENT',
            replyToId,
            createdAt: new Date().toString()
          };
          this.selectedChat.lastMessage = this.messageContent;
          this.chatMessages.push(message);
          this.isScrolledUp = false;
          this.forceScrollOnNextCheck = true;
          this.messageContent = '';
          this.replyingToMessage = null;
          if (this.selectedChat.id) {
            this.draftService.clearDraft(this.selectedChat.id);
          }
          this.showEmojis = false;
          if (this.isChatPending() && this.isRequester()) {
            this.selectedChat.pendingMessageCount = (this.selectedChat.pendingMessageCount ?? 0) + 1;
          }
          if (this.getReceiverId() === ARNO_USER_ID) {
            this.startArnoTyping(this.selectedChat.id as string);
          } else {
            this.sendTypingStop();
          }
        }
      });
    }
  }

  onMessageInput(): void {
    if (!this.selectedChat.id || this.getReceiverId() === ARNO_USER_ID) {
      return;
    }
    this.sendTypingStart();
    if (this.typingStopTimeout !== null) {
      clearTimeout(this.typingStopTimeout);
    }
    this.typingStopTimeout = setTimeout(() => this.sendTypingStop(), TYPING_STOP_DEBOUNCE_MS);
  }

  private sendTypingStart(): void {
    const now = Date.now();
    if (this.typingActive && now - this.lastTypingSentAt < TYPING_SEND_THROTTLE_MS) {
      return;
    }
    this.typingActive = true;
    this.lastTypingSentAt = now;
    this.publishTyping(true);
  }

  private sendTypingStop(): void {
    if (this.typingStopTimeout !== null) {
      clearTimeout(this.typingStopTimeout);
      this.typingStopTimeout = null;
    }
    if (!this.typingActive) {
      return;
    }
    this.typingActive = false;
    this.publishTyping(false);
  }

  private publishTyping(typing: boolean): void {
    if (!this.socketClient?.connected || !this.selectedChat.id) {
      return;
    }
    this.socketClient.publish({
      destination: '/app/chat.typing',
      body: JSON.stringify({
        chatId: this.selectedChat.id,
        receiverId: this.getReceiverId(),
        typing
      })
    });
  }

  private startArnoTyping(chatId: string): void {
    this.peerTypingChatId = chatId;
    if (this.peerTypingTimeout !== null) {
      clearTimeout(this.peerTypingTimeout);
    }
    this.peerTypingTimeout = setTimeout(() => {
      this.ngZone.run(() => this.stopPeerTyping());
    }, ARNO_TYPING_TIMEOUT_MS);
  }

  private handlePeerTyping(notification: Notification): void {
    if (this.selectedChat?.id !== notification.chatId) {
      return;
    }
    if (notification.type === 'TYPING_START') {
      this.peerTypingChatId = notification.chatId ?? null;
      if (this.peerTypingTimeout !== null) {
        clearTimeout(this.peerTypingTimeout);
      }
      this.peerTypingTimeout = setTimeout(() => {
        this.ngZone.run(() => this.stopPeerTyping());
      }, PEER_TYPING_SAFETY_TIMEOUT_MS);
    } else {
      this.stopPeerTyping();
    }
  }

  private stopPeerTyping(): void {
    this.peerTypingChatId = null;
    if (this.peerTypingTimeout !== null) {
      clearTimeout(this.peerTypingTimeout);
      this.peerTypingTimeout = null;
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

  openLightbox(message: MessageResponse): void {
    if (!message.media?.[0] || (message.type !== 'IMAGE' && message.type !== 'VIDEO')) return;
    this.lightboxMedia = { url: this.mediaSrc(message.media[0]), type: message.type };
  }

  closeLightbox(): void {
    this.lightboxMedia = null;
  }

  toggleMessageMenu(id: number | undefined): void {
    if (id === undefined) return;
    this.activeMessageMenuId = this.activeMessageMenuId === id ? null : id;
  }

  findMessageById(id: number | undefined): MessageResponse | undefined {
    if (id === undefined) return undefined;
    return this.chatMessages.find(m => m.id === id);
  }

  startReply(message: MessageResponse): void {
    this.replyingToMessage = message;
  }

  cancelReply(): void {
    this.replyingToMessage = null;
  }

  startForward(message: MessageResponse): void {
    this.forwardingMessage = message;
  }

  cancelForward(): void {
    this.forwardingMessage = null;
  }

  confirmForward(targetChat: ChatResponse): void {
    const message = this.forwardingMessage;
    if (!message || !targetChat.id || message.type !== 'TEXT') {
      this.forwardingMessage = null;
      return;
    }
    const messageRequest: MessageRequest = {
      chatId: targetChat.id,
      content: message.content ?? '',
      type: 'TEXT',
      forwarded: true
    };
    this.messageService.saveMessage({ body: messageRequest }).subscribe({
      next: (response) => {
        targetChat.lastMessage = messageRequest.content;
        targetChat.lastMessageTime = new Date().toString();
        if (this.selectedChat.id === targetChat.id) {
          this.chatMessages.push({
            id: response.id,
            senderId: this.getSenderId(targetChat),
            receiverId: this.getReceiverId(targetChat),
            content: messageRequest.content,
            type: 'TEXT',
            state: 'SENT',
            forwarded: true,
            createdAt: new Date().toString()
          });
          this.isScrolledUp = false;
          this.forceScrollOnNextCheck = true;
        }
      }
    });
    this.forwardingMessage = null;
  }

  copyMessageText(message: MessageResponse): void {
    if (message.type !== 'TEXT' || !message.content) return;
    navigator.clipboard?.writeText(message.content).catch(() => undefined);
  }

  startEdit(message: MessageResponse): void {
    if (message.type !== 'TEXT') return;
    this.editingMessage = message;
    this.editContent = message.content ?? '';
  }

  cancelEdit(): void {
    this.editingMessage = null;
    this.editContent = '';
  }

  openReactionPicker(messageId: number | undefined): void {
    if (messageId === undefined) return;
    this.reactingToMessageId = this.reactingToMessageId === messageId ? null : messageId;
  }

  onReactionSelected(message: MessageResponse, emojiSelected: any): void {
    this.reactingToMessageId = null;
    if (!message.id) return;
    const emoji: EmojiData = emojiSelected.emoji;
    if (!emoji.native) return;
    const messageId = message.id;
    this.messageService.toggleReaction({
      messageId,
      body: { emoji: emoji.native }
    }).subscribe({
      next: (response) => {
        const target = this.findMessageById(messageId);
        if (target) {
          target.reactions = response.reactions;
        }
      }
    });
  }

  confirmDelete(message: MessageResponse): void {
    if (!message.id || message.deleted) return;
    if (!window.confirm('Eliminare questo messaggio?')) return;
    const messageId = message.id;
    message.deleted = true;
    message.content = undefined;
    message.media = undefined;
    this.messageService.deleteMessage({ messageId }).subscribe();
  }

  confirmEdit(): void {
    const messageId = this.editingMessage?.id;
    if (messageId === undefined || !this.editContent.trim()) return;
    this.messageService.editMessage({
      messageId,
      body: { content: this.editContent }
    }).subscribe({
      next: (response) => {
        const target = this.findMessageById(messageId);
        if (target) {
          target.content = response.content;
          target.editedAt = response.editedAt;
        }
        this.cancelEdit();
      }
    });
  }

  private mediaTypeFromFileName(fileName: string): 'IMAGE' | 'VIDEO' | 'AUDIO' {
    const extension = fileName.split('.').pop()?.toLowerCase() ?? '';
    if (['mp4', 'mov'].includes(extension)) {
      return 'VIDEO';
    }
    if (['mp3', 'wav', 'ogg', 'm4a', 'webm'].includes(extension)) {
      return 'AUDIO';
    }
    return 'IMAGE';
  }

  private mediaLabelForType(type: 'TEXT' | 'IMAGE' | 'VIDEO' | 'AUDIO' | undefined): string {
    switch (type) {
      case 'VIDEO': return '🎥 Video';
      case 'AUDIO': return '🎤 Messaggio vocale';
      default: return '📷 Foto';
    }
  }

  private isMediaNotificationType(type: Notification['type']): boolean {
    return type === 'IMAGE' || type === 'VIDEO' || type === 'AUDIO';
  }

  uploadMedia(target: EventTarget | null) {
    const file = this.extractFileFromTarget(target);
    if (file !== null) {
      const mediaType = this.mediaTypeFromFileName(file.name);
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
            next: (response) => {
              const message: MessageResponse = {
                id: response.id,
                senderId: this.getSenderId(),
                receiverId: this.getReceiverId(),
                content: this.mediaLabelForType(mediaType),
                type: mediaType,
                state: 'SENT',
                media: [mediaLines],
                createdAt: new Date().toString()
              };
              this.chatMessages.push(message);
              this.isScrolledUp = false;
              this.forceScrollOnNextCheck = true;
            }
          });
        }
      }
      reader.readAsDataURL(file);
    }
  }

  async startRecording(): Promise<void> {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({audio: true});
      const mimeType = MediaRecorder.isTypeSupported('audio/webm')
        ? 'audio/webm'
        : (MediaRecorder.isTypeSupported('audio/mp4') ? 'audio/mp4' : '');
      this.mediaRecorder = mimeType ? new MediaRecorder(stream, {mimeType}) : new MediaRecorder(stream);
      this.recordedChunks = [];
      this.mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) {
          this.recordedChunks.push(e.data);
        }
      };
      this.mediaRecorder.onstop = () => {
        stream.getTracks().forEach(t => t.stop());
        this.recordedBlob = new Blob(this.recordedChunks, {type: this.mediaRecorder!.mimeType});
        this.recordedPreviewUrl = URL.createObjectURL(this.recordedBlob);
        this.ngZone.run(() => this.recordingState = 'preview');
      };
      this.mediaRecorder.start();
      this.recordingState = 'recording';
      this.recordingElapsedSeconds = 0;
      this.recordingTimerHandle = setInterval(() => {
        this.ngZone.run(() => {
          this.recordingElapsedSeconds++;
          if (this.recordingElapsedSeconds >= MainComponent.MAX_RECORDING_SECONDS) {
            this.stopRecording();
          }
        });
      }, 1000);
    } catch {
      // permesso negato o device non disponibile: resta in idle, nessun crash
    }
  }

  stopRecording(): void {
    if (this.recordingTimerHandle !== null) {
      clearInterval(this.recordingTimerHandle);
      this.recordingTimerHandle = null;
    }
    this.mediaRecorder?.stop();
  }

  cancelRecording(): void {
    if (this.recordingTimerHandle !== null) {
      clearInterval(this.recordingTimerHandle);
      this.recordingTimerHandle = null;
    }
    if (this.mediaRecorder && this.mediaRecorder.state !== 'inactive') {
      this.mediaRecorder.onstop = () => {
        this.mediaRecorder!.stream?.getTracks().forEach(t => t.stop());
      };
      this.mediaRecorder.stop();
    }
    this.recordedChunks = [];
    this.recordingState = 'idle';
  }

  discardRecording(): void {
    if (this.recordedPreviewUrl) {
      URL.revokeObjectURL(this.recordedPreviewUrl);
    }
    this.recordedBlob = null;
    this.recordedPreviewUrl = null;
    this.recordingState = 'idle';
  }

  recordingElapsedLabel(): string {
    const mins = Math.floor(this.recordingElapsedSeconds / 60);
    const secs = this.recordingElapsedSeconds % 60;
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }

  sendRecordedVoiceNote(): void {
    if (!this.recordedBlob) return;
    this.recordingState = 'sending';
    const extension = this.recordedBlob.type.includes('mp4') ? 'm4a' : 'webm';
    const file = new File([this.recordedBlob], `voice-note-${Date.now()}.${extension}`, {type: this.recordedBlob.type});
    this.messageService.uploadMedia({
      'chat-id': this.selectedChat.id as string,
      'media-type': 'AUDIO',
      body: {file}
    }).subscribe({
      next: (response) => {
        const reader = new FileReader();
        reader.onload = () => {
          if (reader.result) {
            const mediaLines = reader.result.toString().split(',')[1];
            const message: MessageResponse = {
              id: response.id,
              senderId: this.getSenderId(),
              receiverId: this.getReceiverId(),
              content: this.mediaLabelForType('AUDIO'),
              type: 'AUDIO',
              state: 'SENT',
              media: [mediaLines],
              createdAt: new Date().toString()
            };
            this.chatMessages.push(message);
            this.isScrolledUp = false;
            this.forceScrollOnNextCheck = true;
          }
          this.discardRecording();
        };
        reader.readAsDataURL(file);
      },
      error: () => {
        this.recordingState = 'preview';
      }
    });
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
    // No userId in the path: Spring's DefaultUserDestinationResolver scopes "/user/**"
    // destinations to the current STOMP session's Principal automatically. Embedding the
    // userId here (as this used to do) breaks the SUBSCRIBE-side destination resolution —
    // it only strips the "/user" prefix for SUBSCRIBE frames, unlike the SEND side, so the
    // physical broker destination ends up different and no delivery ever happens.
    const subUrl = '/user/queue/chat';
    this.socketClient = new Client({
      webSocketFactory: () => new SockJS(`${window.location.origin}/ws`) as any,
      // connectHeaders is captured once at CONNECT time (including every automatic
      // reconnect), not just at Client construction — without refreshing the token here,
      // a STOMP reconnect after the access token expires (accessTokenLifespan, 5 min) keeps
      // resending the same stale JWT forever, so delivery silently dies until the page is
      // reloaded (which rebuilds the Client with a fresh token).
      beforeConnect: async () => {
        await this.keycloakService.keycloak.updateToken(30).catch(() => undefined);
        this.socketClient!.connectHeaders = {
          'Authorization': 'Bearer ' + this.keycloakService.keycloak.token,
          'X-Tab-Id': this.keycloakService.tabId
        };
      },
      reconnectDelay: 5000,
      onConnect: () => {
        this.notificationSubscription = this.socketClient!.subscribe(
          subUrl,
          (message: IMessage) => {
            const notification: Notification = JSON.parse(message.body);
            this.handleNotification(notification);
          }
        );
        this.callSignalSubscription = this.socketClient!.subscribe(
          '/user/queue/call',
          (message: IMessage) => {
            const signal: CallSignal = JSON.parse(message.body);
            this.ngZone.run(() => this.handleCallSignal(signal));
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
      if (notification.type === 'TYPING_START' || notification.type === 'TYPING_STOP') {
        this.handlePeerTyping(notification);
        return;
      }
      if (notification.type === 'CHAT_REQUEST') {
        this.handleChatRequest(notification);
        return;
      }
      if (notification.type === 'CHAT_REQUEST_ACCEPTED') {
        this.handleChatRequestAccepted(notification);
        return;
      }
      if (notification.type === 'CHAT_REQUEST_REJECTED') {
        this.handleChatRequestRejected(notification);
        return;
      }
      if (notification.type === 'MESSAGE_EDITED') {
        this.handleMessageEdited(notification);
        return;
      }
      if (notification.type === 'MESSAGE_DELETED') {
        this.handleMessageDeleted(notification);
        return;
      }
      if (notification.type === 'REACTION_ADDED' || notification.type === 'REACTION_REMOVED') {
        this.handleReactionEvent(notification, notification.type === 'REACTION_ADDED');
        return;
      }
      if (notification.type === 'MESSAGE' || this.isMediaNotificationType(notification.type)) {
        this.maybeShowDesktopNotification(notification);
      }
      if (notification.chatId === this.peerTypingChatId) {
        this.stopPeerTyping();
      }
      if (this.selectedChat && this.selectedChat.id === notification.chatId) {
        if (notification.type === 'MESSAGE' || this.isMediaNotificationType(notification.type)) {
          const message: MessageResponse = {
            id: notification.messageId,
            senderId: notification.senderId,
            receiverId: notification.receiverId,
            content: notification.content,
            type: notification.messageType,
            media: notification.media,
            replyToId: notification.replyToId,
            forwarded: notification.forwarded,
            createdAt: new Date().toString()
          };
          if (this.isMediaNotificationType(notification.type)) {
            this.selectedChat.lastMessage = this.mediaLabelForType(notification.messageType);
          } else {
            this.selectedChat.lastMessage = notification.content;
          }
          this.chatMessages.push(message);
        } else if (notification.type === 'SEEN') {
          this.chatMessages.forEach(m => m.state = 'SEEN');
        }
      } else {
        const destChat = this.chats.find(c => c.id === notification.chatId);
        if (destChat && notification.type !== 'SEEN') {
          if (notification.type === 'MESSAGE') {
            destChat.lastMessage = notification.content;
          } else if (this.isMediaNotificationType(notification.type)) {
            destChat.lastMessage = this.mediaLabelForType(notification.messageType);
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
    if (this.muteService.isMuted(notification.chatId)) return;
    const chatIsOpenAndFocused = document.hasFocus() && this.selectedChat?.id === notification.chatId;
    if (chatIsOpenAndFocused) return;

    const title = notification.chatName || 'Nuovo messaggio';
    const body = this.isMediaNotificationType(notification.type)
      ? `Ti ha inviato ${this.mediaLabelForType(notification.messageType).toLowerCase()}`
      : (notification.content || '');
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

  private handleChatRequest(notification: Notification): void {
    if (this.chats.find(c => c.id === notification.chatId)) return;
    const newChat: ChatResponse = {
      id: notification.chatId,
      senderId: notification.senderId,
      receiverId: notification.receiverId,
      name: notification.chatName,
      status: 'PENDING',
      pendingMessageCount: 0,
      unreadCount: 0
    };
    this.chats.unshift(newChat);
    this.browserNotifications.notify(
      notification.chatName || 'Nuova richiesta di chat',
      'Vuole iniziare una chat con te',
      () => this.ngZone.run(() => {
        const chat = this.chats.find(c => c.id === notification.chatId);
        if (chat) this.chatSelected(chat);
      })
    );
  }

  private handleChatRequestAccepted(notification: Notification): void {
    const chat = this.chats.find(c => c.id === notification.chatId);
    if (chat) chat.status = 'ACCEPTED';
    if (this.selectedChat?.id === notification.chatId) {
      this.selectedChat.status = 'ACCEPTED';
    }
  }

  private handleMessageEdited(notification: Notification): void {
    const target = this.chatMessages.find(m => m.id === notification.messageId);
    if (target) {
      target.content = notification.content;
      target.editedAt = new Date().toString();
    }
  }

  private handleMessageDeleted(notification: Notification): void {
    const target = this.chatMessages.find(m => m.id === notification.messageId);
    if (target) {
      target.deleted = true;
      target.content = undefined;
      target.media = undefined;
    }
  }

  // Delta-patches the aggregate reaction counts from a peer's WS event, without a full
  // reload. The pusher is always the *other* participant relative to whoever reacted, so
  // reactedByMe is never flipped here — only count. Known limitation: when the reactor
  // switches from one emoji to another, the backend sends a single REACTION_ADDED for the
  // new emoji only (no matching REMOVED for the old one, since aggregate summaries carry no
  // per-user breakdown to the peer) — the old emoji's count can look stale here until the
  // chat is reloaded (findChatMessages always recomputes reactions from the DB, so a reload
  // self-heals it).
  private handleReactionEvent(notification: Notification, added: boolean): void {
    const target = this.chatMessages.find(m => m.id === notification.messageId);
    if (!target || !notification.reactionEmoji) return;
    const reactions = target.reactions ? target.reactions.map(r => ({ ...r })) : [];
    const index = reactions.findIndex(r => r.emoji === notification.reactionEmoji);
    if (added) {
      if (index >= 0) {
        reactions[index].count = (reactions[index].count ?? 0) + 1;
      } else {
        reactions.push({ emoji: notification.reactionEmoji, count: 1, reactedByMe: false });
      }
    } else if (index >= 0) {
      const newCount = (reactions[index].count ?? 1) - 1;
      if (newCount <= 0) {
        reactions.splice(index, 1);
      } else {
        reactions[index].count = newCount;
      }
    }
    target.reactions = reactions;
  }

  private handleChatRequestRejected(notification: Notification): void {
    this.chats = this.chats.filter(c => c.id !== notification.chatId);
    if (this.selectedChat?.id === notification.chatId) {
      this.selectedChat = {};
    }
  }

  isChatPending(): boolean {
    return this.selectedChat.status === 'PENDING';
  }

  isChatRejected(): boolean {
    return this.selectedChat.status === 'REJECTED';
  }

  isRequester(): boolean {
    return this.selectedChat.senderId === this.keycloakService.userId;
  }

  pendingLimitReached(): boolean {
    return (this.selectedChat.pendingMessageCount ?? 0) >= MAX_PENDING_MESSAGES;
  }

  acceptSelectedChat(): void {
    this.chatService.acceptChat({ chatId: this.selectedChat.id as string }).subscribe({
      next: () => {
        this.selectedChat.status = 'ACCEPTED';
        const chat = this.chats.find(c => c.id === this.selectedChat.id);
        if (chat) chat.status = 'ACCEPTED';
      }
    });
  }

  goBack(): void {
    this.selectedChat = {};
  }

  rejectSelectedChat(): void {
    const chatId = this.selectedChat.id;
    this.chatService.rejectChat({ chatId: chatId as string }).subscribe({
      next: () => {
        this.chats = this.chats.filter(c => c.id !== chatId);
        this.selectedChat = {};
      }
    });
  }

  private getSenderId(chat: ChatResponse = this.selectedChat): string {
    if (chat.senderId === this.keycloakService.userId) {
      return chat.senderId as string;
    }
    return chat.receiverId as string;
  }

  getReceiverId(chat: ChatResponse = this.selectedChat): string {
    if (chat.senderId === this.keycloakService.userId) {
      return chat.receiverId as string;
    }
    return chat.senderId as string;
  }

  private scrollToBottom() {
    // Skip while an in-conversation search has an active match: a scroll-to-match
    // (see jumpToNextMatch/jumpToPreviousMatch) would otherwise be undone on the very
    // next change-detection cycle (e.g. the next keystroke), since this method runs
    // unconditionally on every ngAfterViewChecked.
    if (this.showMessageSearch && this.searchMatchIndices.length > 0) {
      return;
    }
    if (!this.scrollableDiv) return;
    // Once the user has scrolled up to read history, don't yank them back to the
    // bottom on every change-detection cycle (e.g. the typing-indicator animating) —
    // except right after their own message send, which forces the scroll regardless.
    if (this.isScrolledUp && !this.forceScrollOnNextCheck) {
      return;
    }
    const div = this.scrollableDiv.nativeElement;
    div.scrollTop = div.scrollHeight;
    this.forceScrollOnNextCheck = false;
  }

  onMessagesScroll(): void {
    if (!this.scrollableDiv) return;
    const div = this.scrollableDiv.nativeElement;
    const distanceFromBottom = div.scrollHeight - div.scrollTop - div.clientHeight;
    this.isScrolledUp = distanceFromBottom > SCROLL_THRESHOLD_PX;
  }

  jumpToLatest(): void {
    this.isScrolledUp = false;
    this.forceScrollOnNextCheck = true;
    this.scrollToBottom();
  }

  private isSameDay(a: Date, b: Date): boolean {
    return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
  }

  shouldShowDateSeparator(index: number): boolean {
    if (index === 0) return true;
    const current = this.chatMessages[index]?.createdAt;
    const previous = this.chatMessages[index - 1]?.createdAt;
    if (!current || !previous) return false;
    return !this.isSameDay(new Date(current), new Date(previous));
  }

  dateSeparatorLabel(index: number): string {
    const dateStr = this.chatMessages[index]?.createdAt;
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const today = new Date();
    if (this.isSameDay(date, today)) return 'Oggi';
    const yesterday = new Date(today);
    yesterday.setDate(today.getDate() - 1);
    if (this.isSameDay(date, yesterday)) return 'Ieri';
    return new DatePipe('it-IT').transform(date, 'd MMMM yyyy') ?? '';
  }

  toggleMessageSearch(): void {
    this.showMessageSearch = !this.showMessageSearch;
    if (!this.showMessageSearch) {
      this.messageSearchQuery = '';
      this.searchMatchIndices = [];
      this.currentSearchMatchIndex = -1;
    }
  }

  onMessageSearchInput(): void {
    const query = this.messageSearchQuery.trim().toLowerCase();
    if (!query) {
      this.searchMatchIndices = [];
      this.currentSearchMatchIndex = -1;
      return;
    }
    this.searchMatchIndices = this.chatMessages
      .map((message, index) => ({message, index}))
      .filter(({message}) => message.type === 'TEXT' && (message.content ?? '').toLowerCase().includes(query))
      .map(({index}) => index);
    this.currentSearchMatchIndex = this.searchMatchIndices.length > 0 ? 0 : -1;
    this.scrollToCurrentMatch();
  }

  jumpToNextMatch(): void {
    if (this.searchMatchIndices.length === 0) return;
    this.currentSearchMatchIndex = (this.currentSearchMatchIndex + 1) % this.searchMatchIndices.length;
    this.scrollToCurrentMatch();
  }

  jumpToPreviousMatch(): void {
    if (this.searchMatchIndices.length === 0) return;
    this.currentSearchMatchIndex =
      (this.currentSearchMatchIndex - 1 + this.searchMatchIndices.length) % this.searchMatchIndices.length;
    this.scrollToCurrentMatch();
  }

  isMessageSearchMatch(index: number): boolean {
    return this.searchMatchIndices.includes(index);
  }

  isCurrentSearchMatch(index: number): boolean {
    return this.searchMatchIndices[this.currentSearchMatchIndex] === index;
  }

  private scrollToCurrentMatch(): void {
    if (this.currentSearchMatchIndex < 0 || !this.scrollableDiv) return;
    const messageIndex = this.searchMatchIndices[this.currentSearchMatchIndex];
    const el = this.scrollableDiv.nativeElement.querySelector(`[data-msg-index="${messageIndex}"]`);
    el?.scrollIntoView({behavior: 'smooth', block: 'center'});
  }

  private extractFileFromTarget(target: EventTarget | null): File | null {
    const htmlInputTarget = target as HTMLInputElement;
    if (target === null || htmlInputTarget.files === null) {
      return null;
    }
    return htmlInputTarget.files[0];
  }

  canCall(): boolean {
    return this.callState === 'idle' && this.selectedChat.status === 'ACCEPTED';
  }

  async startCall(callType: 'AUDIO' | 'VIDEO'): Promise<void> {
    if (!this.canCall() || !this.selectedChat.id) return;
    const chatId = this.selectedChat.id;
    const peerId = this.getReceiverId();
    this.activeCallChatId = chatId;
    this.activeCallPeerId = peerId;
    this.activeCallPeerName = this.selectedChat.name ?? null;
    this.activeCallPeerAvatarUrl = this.selectedChat.avatarUrl ?? null;
    this.activeCallType = callType;
    this.callState = 'outgoing';
    try {
      const sdpOffer = await this.webRtcCallService.startAsCaller(callType);
      this.callApiService.invite(chatId, peerId, callType, sdpOffer).subscribe({
        error: () => this.endCallLocally()
      });
    } catch {
      // getUserMedia denied or unavailable: stay out of the call, no crash
      this.endCallLocally();
    }
  }

  acceptCall(): void {
    if (this.callState !== 'incoming' || !this.activeCallChatId || !this.pendingOfferSdp) return;
    const chatId = this.activeCallChatId;
    const offerSdp = this.pendingOfferSdp;
    this.webRtcCallService.startAsCallee(this.activeCallType, offerSdp).then(sdpAnswer => {
      this.callApiService.answer(chatId, sdpAnswer).subscribe({
        next: () => this.ngZone.run(() => this.callState = 'in-call'),
        error: () => this.ngZone.run(() => this.endCallLocally())
      });
    }).catch(() => this.ngZone.run(() => this.endCallLocally()));
  }

  rejectCall(): void {
    if (this.callState !== 'incoming' || !this.activeCallChatId) return;
    this.callApiService.end(this.activeCallChatId, 'REJECT').subscribe();
    this.endCallLocally();
  }

  hangUp(): void {
    if (!this.activeCallChatId) return;
    this.callApiService.end(this.activeCallChatId, 'HANGUP').subscribe();
    this.endCallLocally();
  }

  private handleCallSignal(signal: CallSignal): void {
    if (!signal?.chatId) return;
    switch (signal.type) {
      case 'INVITE':
        if (this.callState !== 'idle') {
          // Already on a call: decline immediately rather than leaving the caller ringing forever.
          this.callApiService.end(signal.chatId, 'REJECT').subscribe();
          return;
        }
        this.activeCallChatId = signal.chatId;
        this.activeCallPeerId = signal.fromUserId ?? null;
        this.activeCallType = signal.callType ?? 'AUDIO';
        this.pendingOfferSdp = signal.sdp ?? null;
        const peerChat = this.chats.find(c => c.senderId === signal.fromUserId || c.receiverId === signal.fromUserId);
        this.activeCallPeerName = peerChat?.name ?? null;
        this.activeCallPeerAvatarUrl = peerChat?.avatarUrl ?? null;
        this.callState = 'incoming';
        break;
      case 'ANSWER':
        if (this.callState === 'outgoing' && this.activeCallChatId === signal.chatId && signal.sdp) {
          this.webRtcCallService.completeAsCaller(signal.sdp).then(() => {
            this.ngZone.run(() => this.callState = 'in-call');
          });
        }
        break;
      case 'ICE_CANDIDATE':
        if (this.activeCallChatId === signal.chatId && signal.candidate) {
          this.webRtcCallService.addRemoteIceCandidate(
            signal.candidate, signal.candidateSdpMid ?? null, signal.candidateSdpMLineIndex ?? null
          );
        }
        break;
      case 'END':
      case 'REJECT':
      case 'BUSY':
      case 'MISSED':
        if (this.activeCallChatId === signal.chatId) {
          this.endCallLocally();
        }
        break;
    }
  }

  private endCallLocally(): void {
    this.webRtcCallService.close();
    this.callState = 'idle';
    this.activeCallChatId = null;
    this.activeCallPeerId = null;
    this.activeCallPeerName = null;
    this.activeCallPeerAvatarUrl = null;
    this.pendingOfferSdp = null;
  }
}
