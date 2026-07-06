export interface Notification {
  chatId?: string;
  content?: string;
  senderId?: string;
  receiverId?: string;
  messageType?: 'TEXT' | 'IMAGE' | 'VIDEO' | 'AUDIO';
  type?: 'SEEN' | 'MESSAGE' | 'IMAGE' | 'VIDEO' | 'AUDIO' | 'AVATAR_UPDATED' | 'CHAT_REQUEST' | 'CHAT_REQUEST_ACCEPTED' | 'CHAT_REQUEST_REJECTED' | 'MESSAGE_EDITED' | 'MESSAGE_DELETED' | 'REACTION_ADDED' | 'REACTION_REMOVED' | 'TYPING_START' | 'TYPING_STOP';
  chatName?: string;
  media?: Array<string>;
  avatarUrl?: string;
  messageId?: number;
  replyToId?: number;
  forwarded?: boolean;
  reactionEmoji?: string;
}
