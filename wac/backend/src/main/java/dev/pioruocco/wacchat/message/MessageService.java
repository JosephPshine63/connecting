package dev.pioruocco.wacchat.message;

import dev.pioruocco.wacchat.chat.Chat;
import dev.pioruocco.wacchat.chat.ChatRepository;
import dev.pioruocco.wacchat.file.FileUtils;
import dev.pioruocco.wacchat.file.R2StorageService;
import dev.pioruocco.wacchat.notification.Notification;
import dev.pioruocco.wacchat.notification.NotificationService;
import dev.pioruocco.wacchat.notification.NotificationType;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final MessageMapper mapper;
    private final NotificationService notificationService;
    private final R2StorageService r2StorageService;

    public void saveMessage(MessageRequest messageRequest, Authentication authentication) {
        Chat chat = chatRepository.findById(messageRequest.getChatId())
                .orElseThrow(() -> new EntityNotFoundException("Chat not found"));

        final String senderId = authentication.getName();
        assertParticipant(chat, senderId);
        final String receiverId = resolveReceiverId(chat, senderId);

        Message message = new Message();
        message.setContent(messageRequest.getContent());
        message.setChat(chat);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setType(messageRequest.getType());
        message.setState(MessageState.SENT);

        messageRepository.save(message);

        Notification notification = Notification.builder()
                .chatId(chat.getId())
                .messageType(messageRequest.getType())
                .content(messageRequest.getContent())
                .senderId(senderId)
                .receiverId(receiverId)
                .type(NotificationType.MESSAGE)
                .chatName(chat.getTargetChatName(senderId))
                .build();

        notificationService.sendNotification(receiverId, notification);
    }

    public List<MessageResponse> findChatMessages(String chatId, Authentication authentication) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found"));
        assertParticipant(chat, authentication.getName());
        return messageRepository.findMessagesByChatId(chatId)
                .stream()
                .map(mapper::toMessageResponse)
                .toList();
    }

    @Transactional
    public void setMessagesToSeen(String chatId, Authentication authentication) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found"));
        assertParticipant(chat, authentication.getName());

        final String recipientId = getRecipientId(chat, authentication);
        messageRepository.setMessagesToSeenByChatId(chatId, MessageState.SEEN);

        Notification notification = Notification.builder()
                .chatId(chat.getId())
                .type(NotificationType.SEEN)
                .receiverId(recipientId)
                .senderId(getSenderId(chat, authentication))
                .build();

        notificationService.sendNotification(recipientId, notification);
    }

    public void uploadMediaMessage(String chatId, MultipartFile file, Authentication authentication) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found"));
        assertParticipant(chat, authentication.getName());

        final String senderId = getSenderId(chat, authentication);
        final String receiverId = getRecipientId(chat, authentication);

        final String mediaUrl = r2StorageService.uploadMessageMedia(file, senderId);
        Message message = new Message();
        message.setReceiverId(receiverId);
        message.setSenderId(senderId);
        message.setState(MessageState.SENT);
        message.setType(MessageType.IMAGE);
        message.setMediaFilePath(mediaUrl);
        message.setChat(chat);
        messageRepository.save(message);

        Notification notification = Notification.builder()
                .chatId(chat.getId())
                .type(NotificationType.IMAGE)
                .senderId(senderId)
                .receiverId(receiverId)
                .messageType(MessageType.IMAGE)
                .media(FileUtils.resolveMedia(mediaUrl))
                .build();

        notificationService.sendNotification(receiverId, notification);
    }

    private void assertParticipant(Chat chat, String userId) {
        if (!chat.getSender().getId().equals(userId) && !chat.getRecipient().getId().equals(userId)) {
            throw new AccessDeniedException("You are not a participant in this chat");
        }
    }

    private String resolveReceiverId(Chat chat, String senderId) {
        return chat.getSender().getId().equals(senderId)
                ? chat.getRecipient().getId()
                : chat.getSender().getId();
    }

    private String getSenderId(Chat chat, Authentication authentication) {
        if (chat.getSender().getId().equals(authentication.getName())) {
            return chat.getSender().getId();
        }
        return chat.getRecipient().getId();
    }

    private String getRecipientId(Chat chat, Authentication authentication) {
        if (chat.getSender().getId().equals(authentication.getName())) {
            return chat.getRecipient().getId();
        }
        return chat.getSender().getId();
    }
}
