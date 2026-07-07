package dev.pioruocco.wacchat.message;

import dev.pioruocco.wacchat.chat.Chat;
import dev.pioruocco.wacchat.chat.ChatMember;
import dev.pioruocco.wacchat.chat.ChatMemberRepository;
import dev.pioruocco.wacchat.chat.ChatRepository;
import dev.pioruocco.wacchat.notification.Notification;
import dev.pioruocco.wacchat.notification.NotificationService;
import dev.pioruocco.wacchat.notification.NotificationType;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Persists a message and pushes it through the same notification pipeline as
 * MessageService.saveMessage, but without requiring an Authentication — sender/receiver
 * are caller-supplied instead of derived from a JWT. Used by BotService for Arno's welcome
 * message and Gemini-generated replies, where there is no human request to authenticate.
 */
@Service
@RequiredArgsConstructor
public class SystemMessageSender {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final NotificationService notificationService;

    public void saveSystemMessage(String chatId, String senderId, String receiverId,
                                   String content, MessageType type) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found"));

        Message message = new Message();
        message.setContent(content);
        message.setChat(chat);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setType(type);
        message.setState(MessageState.SENT);
        messageRepository.save(message);

        Notification notification = Notification.builder()
                .chatId(chatId)
                .messageType(type)
                .content(content)
                .senderId(senderId)
                .receiverId(receiverId)
                .type(NotificationType.MESSAGE)
                .chatName(chat.getTargetChatName(senderId))
                .build();

        notificationService.sendNotification(receiverId, notification);
    }

    /**
     * Same as {@link #saveSystemMessage}, but for GROUP chats — receiverId is left null
     * (same convention regular group messages use) and the notification fans out to every
     * other current member, mirroring MessageService.notifyOtherMembers.
     */
    public void saveGroupSystemMessage(String chatId, String senderId, String content) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found"));

        Message message = new Message();
        message.setContent(content);
        message.setChat(chat);
        message.setSenderId(senderId);
        message.setType(MessageType.TEXT);
        message.setState(MessageState.SENT);
        messageRepository.save(message);

        chatMemberRepository.findByChatId(chatId).stream()
                .map(ChatMember::getUserId)
                .filter(memberId -> !memberId.equals(senderId))
                .forEach(memberId -> notificationService.sendNotification(memberId, Notification.builder()
                        .chatId(chatId)
                        .messageType(MessageType.TEXT)
                        .content(content)
                        .senderId(senderId)
                        .receiverId(memberId)
                        .type(NotificationType.MESSAGE)
                        .chatName(chat.getDisplayName(memberId))
                        .build()));
    }
}
