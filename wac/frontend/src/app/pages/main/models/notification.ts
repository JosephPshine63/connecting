export interface Notification {
  chatId?: string;
  content?: string;
  senderId?: string;
  receiverId?: string;
  messageType?: 'TEXT' | 'IMAGE' | 'VIDEO' | 'AUDIO';
  type?: 'SEEN' | 'MESSAGE' | 'IMAGE' | 'VIDEO' | 'AUDIO' | 'AVATAR_UPDATED' | 'TYPING_START' | 'TYPING_STOP';
  chatName?: string;
  media?: Array<string>;
  avatarUrl?: string;
}
