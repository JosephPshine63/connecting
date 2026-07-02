package dev.pioruocco.wacchat.message;

import dev.pioruocco.wacchat.chat.Chat;
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
}
