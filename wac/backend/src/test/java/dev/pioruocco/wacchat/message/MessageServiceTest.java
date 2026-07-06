package dev.pioruocco.wacchat.message;

import dev.pioruocco.wacchat.bot.BotService;
import dev.pioruocco.wacchat.chat.Chat;
import dev.pioruocco.wacchat.chat.ChatNotAcceptedException;
import dev.pioruocco.wacchat.chat.ChatRepository;
import dev.pioruocco.wacchat.chat.ChatRequestLimitExceededException;
import dev.pioruocco.wacchat.chat.ChatStatus;
import dev.pioruocco.wacchat.file.FileServiceClient;
import dev.pioruocco.wacchat.moderation.ModerationService;
import dev.pioruocco.wacchat.notification.Notification;
import dev.pioruocco.wacchat.notification.NotificationService;
import dev.pioruocco.wacchat.notification.NotificationType;
import dev.pioruocco.wacchat.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    private static final String REQUESTER_ID = "requester-1";
    private static final String RECIPIENT_ID = "recipient-1";

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private MessageMapper mapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private FileServiceClient fileServiceClient;
    @Mock
    private BotService botService;
    @Mock
    private ModerationService moderationService;
    @Mock
    private MessageReactionRepository messageReactionRepository;
    @Mock
    private MessageStarRepository messageStarRepository;
    @Mock
    private Authentication authentication;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(
                messageRepository, chatRepository, mapper, notificationService, fileServiceClient, botService,
                moderationService, messageReactionRepository, messageStarRepository);
    }

    @Test
    void saveMessage_acceptedChat_isAllowedWithoutTouchingPendingCount() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);

        messageService.saveMessage(request(chat.getId()), authentication);

        verify(messageRepository).save(any(Message.class));
        verify(chatRepository, never()).save(any(Chat.class));
    }

    @Test
    void saveMessage_requesterUnderLimitWhilePending_incrementsPendingCount() {
        Chat chat = chat(ChatStatus.PENDING, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);

        messageService.saveMessage(request(chat.getId()), authentication);

        assertThat(chat.getPendingMessageCount()).isEqualTo(1);
        verify(chatRepository).save(chat);
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void saveMessage_requesterAtLimitWhilePending_throwsLimitExceeded() {
        Chat chat = chat(ChatStatus.PENDING, 3);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);

        assertThatThrownBy(() -> messageService.saveMessage(request(chat.getId()), authentication))
                .isInstanceOf(ChatRequestLimitExceededException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void saveMessage_recipientWhilePending_throwsChatNotAccepted() {
        Chat chat = chat(ChatStatus.PENDING, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(RECIPIENT_ID);

        assertThatThrownBy(() -> messageService.saveMessage(request(chat.getId()), authentication))
                .isInstanceOf(ChatNotAcceptedException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void saveMessage_blockedPair_throwsChatNotAccepted() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);
        when(moderationService.isBlocked(REQUESTER_ID, RECIPIENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> messageService.saveMessage(request(chat.getId()), authentication))
                .isInstanceOf(ChatNotAcceptedException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void saveMessage_rejectedChat_throwsChatNotAcceptedForEitherParty() {
        Chat chat = chat(ChatStatus.REJECTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);

        assertThatThrownBy(() -> messageService.saveMessage(request(chat.getId()), authentication))
                .isInstanceOf(ChatNotAcceptedException.class);
    }

    @Test
    void saveMessage_returnsMessageResponseWithPersistedId() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);
        doAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        }).when(messageRepository).save(any(Message.class));
        when(mapper.toMessageResponse(any(Message.class), anyString(), any(), anyBoolean()))
                .thenAnswer(invocation -> MessageResponse.builder().id(invocation.<Message>getArgument(0).getId()).build());

        MessageResponse response = messageService.saveMessage(request(chat.getId()), authentication);

        assertThat(response.getId()).isEqualTo(42L);
    }

    @Test
    void saveMessage_notificationCarriesPersistedMessageId() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);
        doAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        }).when(messageRepository).save(any(Message.class));

        messageService.saveMessage(request(chat.getId()), authentication);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).sendNotification(anyString(), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getMessageId()).isEqualTo(99L);
    }

    @Test
    void saveMessage_withReplyToId_persistsAndIncludesInNotification() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);
        Message repliedTo = new Message();
        repliedTo.setId(7L);
        repliedTo.setChat(chat);
        when(messageRepository.findById(7L)).thenReturn(Optional.of(repliedTo));

        messageService.saveMessage(request(chat.getId(), 7L, null), authentication);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getReplyToId()).isEqualTo(7L);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).sendNotification(anyString(), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getReplyToId()).isEqualTo(7L);
    }

    @Test
    void saveMessage_replyToIdFromDifferentChat_rejected() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);
        Chat otherChat = chat(ChatStatus.ACCEPTED, 0);
        otherChat.setId("chat-2");
        Message repliedTo = new Message();
        repliedTo.setId(7L);
        repliedTo.setChat(otherChat);
        when(messageRepository.findById(7L)).thenReturn(Optional.of(repliedTo));

        assertThatThrownBy(() -> messageService.saveMessage(request(chat.getId(), 7L, null), authentication))
                .isInstanceOf(InvalidReplyException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void saveMessage_forwardedFlag_persistsAndMaps() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);

        messageService.saveMessage(request(chat.getId(), null, true), authentication);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().isForwarded()).isTrue();

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).sendNotification(anyString(), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().isForwarded()).isTrue();
    }

    @Test
    void editMessage_ownTextMessage_updatesContentAndSaves() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        Message message = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(authentication.getName()).thenReturn(REQUESTER_ID);

        messageService.editMessage(5L, new EditMessageRequest("updated"), authentication);

        assertThat(message.getContent()).isEqualTo("updated");
        verify(messageRepository).save(message);
    }

    @Test
    void editMessage_notOwnMessage_throwsAccessDenied() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        Message message = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(authentication.getName()).thenReturn(RECIPIENT_ID);

        assertThatThrownBy(() -> messageService.editMessage(5L, new EditMessageRequest("updated"), authentication))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void editMessage_nonTextMessage_throwsNonEditableMessageException() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        Message message = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        message.setType(MessageType.IMAGE);
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(authentication.getName()).thenReturn(REQUESTER_ID);

        assertThatThrownBy(() -> messageService.editMessage(5L, new EditMessageRequest("updated"), authentication))
                .isInstanceOf(NonEditableMessageException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void editMessage_sendsMessageEditedNotificationToReceiver() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        Message message = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(authentication.getName()).thenReturn(REQUESTER_ID);

        messageService.editMessage(5L, new EditMessageRequest("updated"), authentication);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).sendNotification(org.mockito.ArgumentMatchers.eq(RECIPIENT_ID), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.MESSAGE_EDITED);
        assertThat(notificationCaptor.getValue().getContent()).isEqualTo("updated");
        assertThat(notificationCaptor.getValue().getMessageId()).isEqualTo(5L);
    }

    @Test
    void editMessage_alreadyDeleted_throwsNonEditableMessageException() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        Message message = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        message.setDeleted(true);
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(authentication.getName()).thenReturn(REQUESTER_ID);

        assertThatThrownBy(() -> messageService.editMessage(5L, new EditMessageRequest("updated"), authentication))
                .isInstanceOf(NonEditableMessageException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void deleteMessage_own_marksDeletedAndSendsNotification() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        Message message = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(authentication.getName()).thenReturn(REQUESTER_ID);

        messageService.deleteMessage(5L, authentication);

        assertThat(message.isDeleted()).isTrue();
        verify(messageRepository).save(message);
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).sendNotification(org.mockito.ArgumentMatchers.eq(RECIPIENT_ID), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.MESSAGE_DELETED);
        assertThat(notificationCaptor.getValue().getMessageId()).isEqualTo(5L);
    }

    @Test
    void deleteMessage_notOwn_throwsAccessDenied() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        Message message = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(authentication.getName()).thenReturn(RECIPIENT_ID);

        assertThatThrownBy(() -> messageService.deleteMessage(5L, authentication))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void toggleReaction_new_insertsAndSendsAdded() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        Message message = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(authentication.getName()).thenReturn(RECIPIENT_ID);
        when(messageReactionRepository.findByMessageIdAndUserId(5L, RECIPIENT_ID)).thenReturn(Optional.empty());

        messageService.toggleReaction(5L, "👍", authentication);

        ArgumentCaptor<MessageReaction> reactionCaptor = ArgumentCaptor.forClass(MessageReaction.class);
        verify(messageReactionRepository).save(reactionCaptor.capture());
        assertThat(reactionCaptor.getValue().getMessageId()).isEqualTo(5L);
        assertThat(reactionCaptor.getValue().getUserId()).isEqualTo(RECIPIENT_ID);
        assertThat(reactionCaptor.getValue().getEmoji()).isEqualTo("👍");

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).sendNotification(org.mockito.ArgumentMatchers.eq(REQUESTER_ID), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.REACTION_ADDED);
        assertThat(notificationCaptor.getValue().getReactionEmoji()).isEqualTo("👍");
    }

    @Test
    void toggleReaction_sameEmojiAgain_removesAndSendsRemoved() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        Message message = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(authentication.getName()).thenReturn(RECIPIENT_ID);
        MessageReaction existing = new MessageReaction();
        existing.setMessageId(5L);
        existing.setUserId(RECIPIENT_ID);
        existing.setEmoji("👍");
        when(messageReactionRepository.findByMessageIdAndUserId(5L, RECIPIENT_ID)).thenReturn(Optional.of(existing));

        messageService.toggleReaction(5L, "👍", authentication);

        verify(messageReactionRepository).delete(existing);
        verify(messageReactionRepository, never()).save(any());
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).sendNotification(org.mockito.ArgumentMatchers.eq(REQUESTER_ID), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.REACTION_REMOVED);
    }

    @Test
    void toggleReaction_differentEmoji_replacesAndSendsAdded() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        Message message = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(authentication.getName()).thenReturn(RECIPIENT_ID);
        MessageReaction existing = new MessageReaction();
        existing.setMessageId(5L);
        existing.setUserId(RECIPIENT_ID);
        existing.setEmoji("👍");
        when(messageReactionRepository.findByMessageIdAndUserId(5L, RECIPIENT_ID)).thenReturn(Optional.of(existing));

        messageService.toggleReaction(5L, "❤️", authentication);

        verify(messageReactionRepository, never()).delete(any());
        verify(messageReactionRepository).save(existing);
        assertThat(existing.getEmoji()).isEqualTo("❤️");
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).sendNotification(org.mockito.ArgumentMatchers.eq(REQUESTER_ID), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.REACTION_ADDED);
        assertThat(notificationCaptor.getValue().getReactionEmoji()).isEqualTo("❤️");
    }

    @Test
    void toggleReaction_nonParticipant_throwsAccessDenied() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        Message message = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(authentication.getName()).thenReturn("stranger");

        assertThatThrownBy(() -> messageService.toggleReaction(5L, "👍", authentication))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(messageReactionRepository, never()).save(any());
    }

    @Test
    void findChatMessages_aggregatesReactionsPerMessage_andSetsReactedByMeCorrectly() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);

        Message message1 = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        message1.setId(1L);
        Message message2 = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        message2.setId(2L);
        when(messageRepository.findMessagesByChatId(chat.getId())).thenReturn(java.util.List.of(message1, message2));

        MessageReaction reactionOnMessage1 = new MessageReaction();
        reactionOnMessage1.setMessageId(1L);
        reactionOnMessage1.setUserId(REQUESTER_ID);
        reactionOnMessage1.setEmoji("👍");
        when(messageReactionRepository.findByMessageIdIn(java.util.List.of(1L, 2L)))
                .thenReturn(java.util.List.of(reactionOnMessage1));

        messageService.findChatMessages(chat.getId(), authentication);

        ArgumentCaptor<java.util.List<MessageReaction>> reactionsCaptor = ArgumentCaptor.forClass(java.util.List.class);
        verify(mapper).toMessageResponse(org.mockito.ArgumentMatchers.eq(message1), org.mockito.ArgumentMatchers.eq(REQUESTER_ID), reactionsCaptor.capture(), anyBoolean());
        assertThat(reactionsCaptor.getValue()).containsExactly(reactionOnMessage1);

        verify(mapper).toMessageResponse(org.mockito.ArgumentMatchers.eq(message2), org.mockito.ArgumentMatchers.eq(REQUESTER_ID), reactionsCaptor.capture(), anyBoolean());
        assertThat(reactionsCaptor.getValue()).isEmpty();
    }

    @Test
    void findChatMessages_starredMessageForViewer_passesStarredTrueToMapper() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);

        Message message1 = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        message1.setId(1L);
        Message message2 = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        message2.setId(2L);
        when(messageRepository.findMessagesByChatId(chat.getId())).thenReturn(java.util.List.of(message1, message2));

        MessageStar starOnMessage1ByViewer = new MessageStar();
        starOnMessage1ByViewer.setMessageId(1L);
        starOnMessage1ByViewer.setUserId(REQUESTER_ID);
        MessageStar starOnMessage2ByOtherUser = new MessageStar();
        starOnMessage2ByOtherUser.setMessageId(2L);
        starOnMessage2ByOtherUser.setUserId(RECIPIENT_ID);
        when(messageStarRepository.findByMessageIdIn(java.util.List.of(1L, 2L)))
                .thenReturn(java.util.List.of(starOnMessage1ByViewer, starOnMessage2ByOtherUser));

        messageService.findChatMessages(chat.getId(), authentication);

        verify(mapper).toMessageResponse(org.mockito.ArgumentMatchers.eq(message1), org.mockito.ArgumentMatchers.eq(REQUESTER_ID), any(), org.mockito.ArgumentMatchers.eq(true));
        verify(mapper).toMessageResponse(org.mockito.ArgumentMatchers.eq(message2), org.mockito.ArgumentMatchers.eq(REQUESTER_ID), any(), org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void toggleStar_notYetStarred_savesStarAndReturnsStarredTrue() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        Message message = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(authentication.getName()).thenReturn(RECIPIENT_ID);
        when(messageStarRepository.findByMessageIdAndUserId(5L, RECIPIENT_ID)).thenReturn(Optional.empty());
        when(mapper.toMessageResponse(any(Message.class), anyString(), any(), anyBoolean()))
                .thenAnswer(invocation -> MessageResponse.builder().starred(invocation.getArgument(3)).build());

        MessageResponse response = messageService.toggleStar(5L, authentication);

        ArgumentCaptor<MessageStar> starCaptor = ArgumentCaptor.forClass(MessageStar.class);
        verify(messageStarRepository).save(starCaptor.capture());
        assertThat(starCaptor.getValue().getMessageId()).isEqualTo(5L);
        assertThat(starCaptor.getValue().getUserId()).isEqualTo(RECIPIENT_ID);
        assertThat(response.isStarred()).isTrue();
        verify(notificationService, never()).sendNotification(anyString(), any());
    }

    @Test
    void toggleStar_alreadyStarred_removesStarAndReturnsStarredFalse() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        Message message = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(authentication.getName()).thenReturn(RECIPIENT_ID);
        MessageStar existing = new MessageStar();
        existing.setMessageId(5L);
        existing.setUserId(RECIPIENT_ID);
        when(messageStarRepository.findByMessageIdAndUserId(5L, RECIPIENT_ID)).thenReturn(Optional.of(existing));
        when(mapper.toMessageResponse(any(Message.class), anyString(), any(), anyBoolean()))
                .thenAnswer(invocation -> MessageResponse.builder().starred(invocation.getArgument(3)).build());

        MessageResponse response = messageService.toggleStar(5L, authentication);

        verify(messageStarRepository).delete(existing);
        verify(messageStarRepository, never()).save(any());
        assertThat(response.isStarred()).isFalse();
        verify(notificationService, never()).sendNotification(anyString(), any());
    }

    @Test
    void toggleStar_nonParticipant_throwsAccessDenied() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        Message message = existingTextMessage(chat, REQUESTER_ID, RECIPIENT_ID);
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(authentication.getName()).thenReturn("stranger");

        assertThatThrownBy(() -> messageService.toggleStar(5L, authentication))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(messageStarRepository, never()).save(any());
    }

    private static Message existingTextMessage(Chat chat, String senderId, String receiverId) {
        Message message = new Message();
        message.setId(5L);
        message.setChat(chat);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent("original");
        message.setType(MessageType.TEXT);
        message.setState(MessageState.SENT);
        return message;
    }

    @Test
    void uploadMediaMessage_mp4File_resolvesVideoTypeInsteadOfHardcodedImage() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getTokenValue()).thenReturn("token");
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(fileServiceClient.uploadMessageMedia(any(MultipartFile.class), anyString(), anyString()))
                .thenReturn("https://cdn.example.com/messages/requester-1/some-uuid.mp4");

        messageService.uploadMediaMessage(chat.getId(), mock(MultipartFile.class), null, authentication);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getType()).isEqualTo(MessageType.VIDEO);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).sendNotification(anyString(), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.VIDEO);
        assertThat(notificationCaptor.getValue().getMessageType()).isEqualTo(MessageType.VIDEO);
    }

    @Test
    void uploadMediaMessage_mp3File_resolvesAudioTypeInsteadOfHardcodedImage() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getTokenValue()).thenReturn("token");
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(fileServiceClient.uploadMessageMedia(any(MultipartFile.class), anyString(), anyString()))
                .thenReturn("https://cdn.example.com/messages/requester-1/some-uuid.mp3");

        messageService.uploadMediaMessage(chat.getId(), mock(MultipartFile.class), null, authentication);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getType()).isEqualTo(MessageType.AUDIO);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).sendNotification(anyString(), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.AUDIO);
        assertThat(notificationCaptor.getValue().getMessageType()).isEqualTo(MessageType.AUDIO);
    }

    @Test
    void uploadMediaMessage_jpgFile_stillResolvesImageType() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getTokenValue()).thenReturn("token");
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(fileServiceClient.uploadMessageMedia(any(MultipartFile.class), anyString(), anyString()))
                .thenReturn("https://cdn.example.com/messages/requester-1/some-uuid.jpg");

        messageService.uploadMediaMessage(chat.getId(), mock(MultipartFile.class), null, authentication);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getType()).isEqualTo(MessageType.IMAGE);
    }

    @Test
    void uploadMediaMessage_webmFileWithAudioHint_overridesAmbiguousResolver() {
        Chat chat = chat(ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getTokenValue()).thenReturn("token");
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(fileServiceClient.uploadMessageMedia(any(MultipartFile.class), anyString(), anyString()))
                .thenReturn("https://cdn.example.com/messages/requester-1/some-uuid.webm");

        messageService.uploadMediaMessage(chat.getId(), mock(MultipartFile.class), MessageType.AUDIO, authentication);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getType()).isEqualTo(MessageType.AUDIO);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).sendNotification(anyString(), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.AUDIO);
        assertThat(notificationCaptor.getValue().getMessageType()).isEqualTo(MessageType.AUDIO);
    }

    private static MessageRequest request(String chatId) {
        return request(chatId, null, null);
    }

    private static MessageRequest request(String chatId, Long replyToId, Boolean forwarded) {
        return MessageRequest.builder()
                .chatId(chatId)
                .content("hello")
                .type(MessageType.TEXT)
                .replyToId(replyToId)
                .forwarded(forwarded)
                .build();
    }

    private static Chat chat(ChatStatus status, int pendingMessageCount) {
        Chat chat = new Chat();
        chat.setId("chat-1");
        chat.setSender(user(REQUESTER_ID));
        chat.setRecipient(user(RECIPIENT_ID));
        chat.setStatus(status);
        chat.setPendingMessageCount(pendingMessageCount);
        return chat;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
