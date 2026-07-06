package dev.pioruocco.wacchat.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatMapper {

    @Value("${application.admin.user-id:}")
    private String adminUserId;

    public ChatResponse toChatResponse(Chat chat, String senderId) {
        String otherUserId = chat.getSender().getId().equals(senderId)
                ? chat.getRecipient().getId()
                : chat.getSender().getId();
        return ChatResponse.builder()
                .id(chat.getId())
                .name(chat.getChatName(senderId))
                .unreadCount(chat.getUnreadMessages(senderId))
                .lastMessage(chat.getLastMessage())
                .lastMessageType(chat.getLastMessageType())
                .lastMessageTime(chat.getLastMessageTime())
                .isRecipientOnline(chat.getRecipient().isUserOnline())
                .senderId(chat.getSender().getId())
                .receiverId(chat.getRecipient().getId())
                .avatarUrl(chat.getChatAvatarUrl(senderId))
                .status(chat.getStatus())
                .pendingMessageCount(chat.getPendingMessageCount())
                .favorite(chat.isFavorite(senderId))
                .archived(chat.isArchived(senderId))
                .isAdminChat(!adminUserId.isBlank() && adminUserId.equals(otherUserId))
                .build();
    }
}
