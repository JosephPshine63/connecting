package dev.pioruocco.wacchat.chat;

import dev.pioruocco.wacchat.moderation.ModerationService;
import dev.pioruocco.wacchat.moderation.UserBlockedException;
import dev.pioruocco.wacchat.notification.Notification;
import dev.pioruocco.wacchat.notification.NotificationService;
import dev.pioruocco.wacchat.notification.NotificationType;
import dev.pioruocco.wacchat.user.User;
import dev.pioruocco.wacchat.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final ChatMapper mapper;
    private final NotificationService notificationService;
    private final ModerationService moderationService;

    @Transactional(readOnly = true)
    public List<ChatResponse> getChatsByReceiverId(Authentication currentUser) {
        final String userId = currentUser.getName();
        return chatRepository.findChatsBySenderId(userId)
                .stream()
                .map(c -> mapper.toChatResponse(c, userId))
                .toList();
    }

    public String createChat(String senderId, String receiverId) {
        if (moderationService.isBlocked(senderId, receiverId)) {
            throw new UserBlockedException(senderId, receiverId);
        }
        return getOrCreateChat(senderId, receiverId, ChatStatus.PENDING).getId();
    }

    public String createSystemChat(String senderId, String receiverId) {
        return getOrCreateChat(senderId, receiverId, ChatStatus.ACCEPTED).getId();
    }

    public void acceptChat(String chatId, String currentUserId) {
        Chat chat = findChatOrThrow(chatId);
        assertRecipient(chat, currentUserId);
        if (chat.getStatus() != ChatStatus.PENDING) {
            throw new ChatNotAcceptedException(chatId);
        }
        chat.setStatus(ChatStatus.ACCEPTED);
        chatRepository.save(chat);

        notificationService.sendNotification(chat.getSender().getId(), Notification.builder()
                .type(NotificationType.CHAT_REQUEST_ACCEPTED)
                .chatId(chat.getId())
                .senderId(currentUserId)
                .receiverId(chat.getSender().getId())
                .chatName(chat.getTargetChatName(currentUserId))
                .build());
    }

    public void rejectChat(String chatId, String currentUserId) {
        Chat chat = findChatOrThrow(chatId);
        assertRecipient(chat, currentUserId);
        if (chat.getStatus() != ChatStatus.PENDING) {
            throw new ChatNotAcceptedException(chatId);
        }
        chat.setStatus(ChatStatus.REJECTED);
        chatRepository.save(chat);

        notificationService.sendNotification(chat.getSender().getId(), Notification.builder()
                .type(NotificationType.CHAT_REQUEST_REJECTED)
                .chatId(chat.getId())
                .senderId(currentUserId)
                .receiverId(chat.getSender().getId())
                .chatName(chat.getTargetChatName(currentUserId))
                .build());
    }

    private Chat getOrCreateChat(String senderId, String receiverId, ChatStatus initialStatus) {
        Optional<Chat> existingChat = chatRepository.findChatByReceiverAndSender(senderId, receiverId);

        if (existingChat.isPresent()) {
            Chat chat = existingChat.get();
            if (chat.getStatus() != ChatStatus.REJECTED) {
                return chat;
            }
            User sender = findUserOrThrow(senderId);
            User receiver = findUserOrThrow(receiverId);
            chat.setSender(sender);
            chat.setRecipient(receiver);
            chat.setStatus(initialStatus);
            chat.setPendingMessageCount(0);
            Chat revivedChat = chatRepository.save(chat);
            notifyChatRequested(revivedChat, initialStatus);
            return revivedChat;
        }

        User sender = findUserOrThrow(senderId);
        User receiver = findUserOrThrow(receiverId);

        Chat chat = new Chat();
        chat.setSender(sender);
        chat.setRecipient(receiver);
        chat.setStatus(initialStatus);
        chat.setPendingMessageCount(0);

        Chat savedChat = chatRepository.save(chat);
        notifyChatRequested(savedChat, initialStatus);
        return savedChat;
    }

    private void notifyChatRequested(Chat chat, ChatStatus initialStatus) {
        if (initialStatus != ChatStatus.PENDING) {
            return;
        }
        final String requesterId = chat.getSender().getId();
        notificationService.sendNotification(chat.getRecipient().getId(), Notification.builder()
                .type(NotificationType.CHAT_REQUEST)
                .chatId(chat.getId())
                .senderId(requesterId)
                .receiverId(chat.getRecipient().getId())
                .chatName(chat.getTargetChatName(requesterId))
                .build());
    }

    public boolean toggleFavorite(String chatId, String currentUserId) {
        Chat chat = findChatOrThrow(chatId);
        assertParticipant(chat, currentUserId);
        boolean isSender = chat.getSender().getId().equals(currentUserId);
        boolean newValue = isSender ? !chat.isSenderFavorite() : !chat.isRecipientFavorite();
        if (isSender) {
            chat.setSenderFavorite(newValue);
        } else {
            chat.setRecipientFavorite(newValue);
        }
        chatRepository.save(chat);
        return newValue;
    }

    private void assertParticipant(Chat chat, String userId) {
        if (!chat.getSender().getId().equals(userId) && !chat.getRecipient().getId().equals(userId)) {
            throw new AccessDeniedException("You are not a participant in this chat");
        }
    }

    private Chat findChatOrThrow(String chatId) {
        return chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat with id " + chatId + " not found"));
    }

    private User findUserOrThrow(String userId) {
        return userRepository.findByPublicId(userId)
                .orElseThrow(() -> new EntityNotFoundException("User with id " + userId + " not found"));
    }

    private void assertRecipient(Chat chat, String userId) {
        if (!chat.getRecipient().getId().equals(userId)) {
            throw new AccessDeniedException("Only the recipient can respond to this chat request");
        }
    }
}
