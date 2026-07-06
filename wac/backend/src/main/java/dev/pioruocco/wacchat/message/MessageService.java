package dev.pioruocco.wacchat.message;

import dev.pioruocco.wacchat.bot.BotConstants;
import dev.pioruocco.wacchat.bot.BotService;
import dev.pioruocco.wacchat.chat.Chat;
import dev.pioruocco.wacchat.chat.ChatConstants;
import dev.pioruocco.wacchat.chat.ChatNotAcceptedException;
import dev.pioruocco.wacchat.chat.ChatRepository;
import dev.pioruocco.wacchat.chat.ChatRequestLimitExceededException;
import dev.pioruocco.wacchat.chat.ChatStatus;
import dev.pioruocco.wacchat.file.FileServiceClient;
import dev.pioruocco.wacchat.file.FileUtils;
import dev.pioruocco.wacchat.moderation.ModerationService;
import dev.pioruocco.wacchat.notification.Notification;
import dev.pioruocco.wacchat.notification.NotificationService;
import dev.pioruocco.wacchat.notification.NotificationType;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final MessageMapper mapper;
    private final NotificationService notificationService;
    private final FileServiceClient fileServiceClient;
    private final BotService botService;
    private final ModerationService moderationService;
    private final MessageReactionRepository messageReactionRepository;

    public MessageResponse saveMessage(MessageRequest messageRequest, Authentication authentication) {
        Chat chat = chatRepository.findById(messageRequest.getChatId())
                .orElseThrow(() -> new EntityNotFoundException("Chat not found"));

        final String senderId = authentication.getName();
        assertParticipant(chat, senderId);
        assertCanSendMessage(chat, senderId);
        final String receiverId = resolveReceiverId(chat, senderId);

        Long replyToId = messageRequest.getReplyToId();
        if (replyToId != null) {
            Message repliedTo = messageRepository.findById(replyToId)
                    .orElseThrow(() -> new InvalidReplyException(replyToId));
            if (!repliedTo.getChat().getId().equals(chat.getId())) {
                throw new InvalidReplyException(replyToId);
            }
        }
        boolean forwarded = Boolean.TRUE.equals(messageRequest.getForwarded());

        Message message = new Message();
        message.setContent(messageRequest.getContent());
        message.setChat(chat);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setType(messageRequest.getType());
        message.setState(MessageState.SENT);
        message.setReplyToId(replyToId);
        message.setForwarded(forwarded);

        messageRepository.save(message);

        Notification notification = Notification.builder()
                .chatId(chat.getId())
                .messageType(messageRequest.getType())
                .content(messageRequest.getContent())
                .senderId(senderId)
                .receiverId(receiverId)
                .type(NotificationType.MESSAGE)
                .chatName(chat.getTargetChatName(senderId))
                .messageId(message.getId())
                .replyToId(replyToId)
                .forwarded(forwarded)
                .build();

        notificationService.sendNotification(receiverId, notification);

        if (BotConstants.ARNO_USER_ID.equals(receiverId) && messageRequest.getType() == MessageType.TEXT) {
            botService.generateAndSendReply(chat.getId(), senderId);
        }

        return mapper.toMessageResponse(message, senderId, List.of());
    }

    public MessageResponse editMessage(Long messageId, EditMessageRequest request, Authentication authentication) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new EntityNotFoundException("Message not found"));
        assertOwnMessage(message, authentication.getName());
        if (message.isDeleted()) {
            throw new NonEditableMessageException(messageId);
        }
        if (message.getType() != MessageType.TEXT) {
            throw new NonEditableMessageException(messageId);
        }

        message.setContent(request.getContent());
        messageRepository.save(message);

        Notification notification = Notification.builder()
                .chatId(message.getChat().getId())
                .type(NotificationType.MESSAGE_EDITED)
                .senderId(message.getSenderId())
                .receiverId(message.getReceiverId())
                .content(request.getContent())
                .messageId(message.getId())
                .build();
        notificationService.sendNotification(message.getReceiverId(), notification);

        return mapper.toMessageResponse(message, authentication.getName(), messageReactionRepository.findByMessageId(messageId));
    }

    public void deleteMessage(Long messageId, Authentication authentication) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new EntityNotFoundException("Message not found"));
        assertOwnMessage(message, authentication.getName());

        message.setDeleted(true);
        messageRepository.save(message);

        Notification notification = Notification.builder()
                .chatId(message.getChat().getId())
                .type(NotificationType.MESSAGE_DELETED)
                .senderId(message.getSenderId())
                .receiverId(message.getReceiverId())
                .messageId(message.getId())
                .build();
        notificationService.sendNotification(message.getReceiverId(), notification);
    }

    private void assertOwnMessage(Message message, String userId) {
        if (!message.getSenderId().equals(userId)) {
            throw new AccessDeniedException("You can only modify your own messages");
        }
    }

    public List<MessageResponse> findChatMessages(String chatId, Authentication authentication) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found"));
        final String viewerId = authentication.getName();
        assertParticipant(chat, viewerId);

        List<Message> messages = messageRepository.findMessagesByChatId(chatId);
        List<Long> messageIds = messages.stream().map(Message::getId).toList();
        Map<Long, List<MessageReaction>> reactionsByMessageId = messageReactionRepository.findByMessageIdIn(messageIds)
                .stream()
                .collect(Collectors.groupingBy(MessageReaction::getMessageId));

        return messages.stream()
                .map(message -> mapper.toMessageResponse(message, viewerId, reactionsByMessageId.getOrDefault(message.getId(), List.of())))
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

    public MessageResponse uploadMediaMessage(String chatId, MultipartFile file, MessageType mediaTypeHint, Authentication authentication) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new EntityNotFoundException("Chat not found"));
        assertParticipant(chat, authentication.getName());
        assertCanSendMessage(chat, authentication.getName());

        final String senderId = getSenderId(chat, authentication);
        final String receiverId = getRecipientId(chat, authentication);

        final String mediaUrl = fileServiceClient.uploadMessageMedia(file, senderId, bearerToken(authentication));
        final MessageType mediaType = mediaTypeHint != null ? mediaTypeHint : MediaTypeResolver.fromUrl(mediaUrl);

        Message message = new Message();
        message.setReceiverId(receiverId);
        message.setSenderId(senderId);
        message.setState(MessageState.SENT);
        message.setType(mediaType);
        message.setMediaFilePath(mediaUrl);
        message.setChat(chat);
        messageRepository.save(message);

        Notification notification = Notification.builder()
                .chatId(chat.getId())
                .type(toNotificationType(mediaType))
                .senderId(senderId)
                .receiverId(receiverId)
                .messageType(mediaType)
                .media(FileUtils.resolveMedia(mediaUrl))
                .messageId(message.getId())
                .build();

        notificationService.sendNotification(receiverId, notification);

        return mapper.toMessageResponse(message, senderId, List.of());
    }

    public MessageResponse toggleReaction(Long messageId, String emoji, Authentication authentication) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new EntityNotFoundException("Message not found"));
        final String userId = authentication.getName();
        assertParticipant(message.getChat(), userId);

        Optional<MessageReaction> existing = messageReactionRepository.findByMessageIdAndUserId(messageId, userId);
        NotificationType eventType;
        if (existing.isPresent() && existing.get().getEmoji().equals(emoji)) {
            messageReactionRepository.delete(existing.get());
            eventType = NotificationType.REACTION_REMOVED;
        } else if (existing.isPresent()) {
            MessageReaction reaction = existing.get();
            reaction.setEmoji(emoji);
            messageReactionRepository.save(reaction);
            eventType = NotificationType.REACTION_ADDED;
        } else {
            MessageReaction reaction = new MessageReaction();
            reaction.setMessageId(messageId);
            reaction.setUserId(userId);
            reaction.setEmoji(emoji);
            messageReactionRepository.save(reaction);
            eventType = NotificationType.REACTION_ADDED;
        }

        Chat chat = message.getChat();
        String otherParticipant = chat.getSender().getId().equals(userId)
                ? chat.getRecipient().getId()
                : chat.getSender().getId();

        Notification notification = Notification.builder()
                .chatId(chat.getId())
                .type(eventType)
                .senderId(userId)
                .receiverId(otherParticipant)
                .messageId(messageId)
                .reactionEmoji(emoji)
                .build();
        notificationService.sendNotification(otherParticipant, notification);

        return mapper.toMessageResponse(message, userId, messageReactionRepository.findByMessageId(messageId));
    }

    private static String bearerToken(Authentication authentication) {
        return ((Jwt) authentication.getPrincipal()).getTokenValue();
    }

    private static NotificationType toNotificationType(MessageType messageType) {
        return switch (messageType) {
            case IMAGE -> NotificationType.IMAGE;
            case VIDEO -> NotificationType.VIDEO;
            case AUDIO -> NotificationType.AUDIO;
            case TEXT -> NotificationType.MESSAGE;
        };
    }

    private void assertParticipant(Chat chat, String userId) {
        if (!chat.getSender().getId().equals(userId) && !chat.getRecipient().getId().equals(userId)) {
            throw new AccessDeniedException("You are not a participant in this chat");
        }
    }

    private void assertCanSendMessage(Chat chat, String senderId) {
        if (moderationService.isBlocked(chat.getSender().getId(), chat.getRecipient().getId())) {
            throw new ChatNotAcceptedException(chat.getId());
        }
        if (chat.getStatus() == ChatStatus.ACCEPTED) {
            return;
        }
        if (chat.getStatus() == ChatStatus.REJECTED || !chat.getSender().getId().equals(senderId)) {
            throw new ChatNotAcceptedException(chat.getId());
        }
        if (chat.getPendingMessageCount() >= ChatConstants.MAX_PENDING_MESSAGES) {
            throw new ChatRequestLimitExceededException(chat.getId());
        }
        chat.setPendingMessageCount(chat.getPendingMessageCount() + 1);
        chatRepository.save(chat);
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
