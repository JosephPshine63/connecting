package dev.pioruocco.wacchat.chat;

import dev.pioruocco.wacchat.moderation.ModerationService;
import dev.pioruocco.wacchat.moderation.UserBlockedException;
import dev.pioruocco.wacchat.notification.Notification;
import dev.pioruocco.wacchat.notification.NotificationService;
import dev.pioruocco.wacchat.notification.NotificationType;
import dev.pioruocco.wacchat.user.User;
import dev.pioruocco.wacchat.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final String SENDER_ID = "sender-1";
    private static final String RECEIVER_ID = "receiver-1";

    @Mock
    private ChatRepository chatRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ModerationService moderationService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatRepository, userRepository, new ChatMapper(), notificationService, moderationService);
    }

    @Test
    void createChat_blockedPair_throwsUserBlockedException() {
        when(moderationService.isBlocked(SENDER_ID, RECEIVER_ID)).thenReturn(true);

        assertThatThrownBy(() -> chatService.createChat(SENDER_ID, RECEIVER_ID))
                .isInstanceOf(UserBlockedException.class);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void createChat_newPair_startsPendingAndNotifiesReceiver() {
        when(moderationService.isBlocked(SENDER_ID, RECEIVER_ID)).thenReturn(false);
        when(chatRepository.findChatByReceiverAndSender(SENDER_ID, RECEIVER_ID)).thenReturn(Optional.empty());
        when(userRepository.findByPublicId(SENDER_ID)).thenReturn(Optional.of(user(SENDER_ID)));
        when(userRepository.findByPublicId(RECEIVER_ID)).thenReturn(Optional.of(user(RECEIVER_ID)));
        when(chatRepository.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String chatId = chatService.createChat(SENDER_ID, RECEIVER_ID);

        ArgumentCaptor<Chat> savedChat = ArgumentCaptor.forClass(Chat.class);
        verify(chatRepository).save(savedChat.capture());
        assertThat(savedChat.getValue().getStatus()).isEqualTo(ChatStatus.PENDING);
        assertThat(savedChat.getValue().getPendingMessageCount()).isZero();
        assertThat(chatId).isEqualTo(savedChat.getValue().getId());

        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).sendNotification(eq(RECEIVER_ID), notification.capture());
        assertThat(notification.getValue().getType()).isEqualTo(NotificationType.CHAT_REQUEST);
    }

    @Test
    void createSystemChat_newPair_startsAcceptedWithoutNotification() {
        when(chatRepository.findChatByReceiverAndSender(SENDER_ID, RECEIVER_ID)).thenReturn(Optional.empty());
        when(userRepository.findByPublicId(SENDER_ID)).thenReturn(Optional.of(user(SENDER_ID)));
        when(userRepository.findByPublicId(RECEIVER_ID)).thenReturn(Optional.of(user(RECEIVER_ID)));
        when(chatRepository.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatService.createSystemChat(SENDER_ID, RECEIVER_ID);

        ArgumentCaptor<Chat> savedChat = ArgumentCaptor.forClass(Chat.class);
        verify(chatRepository).save(savedChat.capture());
        assertThat(savedChat.getValue().getStatus()).isEqualTo(ChatStatus.ACCEPTED);
        verify(notificationService, never()).sendNotification(any(), any());
    }

    @Test
    void createChat_existingPendingChat_isIdempotentAndDoesNotRenotify() {
        when(moderationService.isBlocked(SENDER_ID, RECEIVER_ID)).thenReturn(false);
        Chat existing = chat(SENDER_ID, RECEIVER_ID, ChatStatus.PENDING, 1);
        when(chatRepository.findChatByReceiverAndSender(SENDER_ID, RECEIVER_ID)).thenReturn(Optional.of(existing));

        String chatId = chatService.createChat(SENDER_ID, RECEIVER_ID);

        assertThat(chatId).isEqualTo(existing.getId());
        verify(chatRepository, never()).save(any());
        verify(notificationService, never()).sendNotification(any(), any());
    }

    @Test
    void createChat_existingRejectedChat_revivesAsNewPendingRequest() {
        when(moderationService.isBlocked(RECEIVER_ID, SENDER_ID)).thenReturn(false);
        Chat existing = chat(SENDER_ID, RECEIVER_ID, ChatStatus.REJECTED, 3);
        when(chatRepository.findChatByReceiverAndSender(RECEIVER_ID, SENDER_ID)).thenReturn(Optional.of(existing));
        when(userRepository.findByPublicId(RECEIVER_ID)).thenReturn(Optional.of(user(RECEIVER_ID)));
        when(userRepository.findByPublicId(SENDER_ID)).thenReturn(Optional.of(user(SENDER_ID)));
        when(chatRepository.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // the original recipient now re-requests the original sender
        chatService.createChat(RECEIVER_ID, SENDER_ID);

        assertThat(existing.getStatus()).isEqualTo(ChatStatus.PENDING);
        assertThat(existing.getPendingMessageCount()).isZero();
        assertThat(existing.getSender().getId()).isEqualTo(RECEIVER_ID);
        assertThat(existing.getRecipient().getId()).isEqualTo(SENDER_ID);

        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).sendNotification(eq(SENDER_ID), notification.capture());
        assertThat(notification.getValue().getType()).isEqualTo(NotificationType.CHAT_REQUEST);
    }

    @Test
    void acceptChat_byRecipient_marksAcceptedAndNotifiesSender() {
        Chat chat = chat(SENDER_ID, RECEIVER_ID, ChatStatus.PENDING, 2);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        chatService.acceptChat(chat.getId(), RECEIVER_ID);

        assertThat(chat.getStatus()).isEqualTo(ChatStatus.ACCEPTED);
        verify(chatRepository).save(chat);
        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).sendNotification(eq(SENDER_ID), notification.capture());
        assertThat(notification.getValue().getType()).isEqualTo(NotificationType.CHAT_REQUEST_ACCEPTED);
    }

    @Test
    void acceptChat_byNonRecipient_throwsAccessDenied() {
        Chat chat = chat(SENDER_ID, RECEIVER_ID, ChatStatus.PENDING, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        assertThatThrownBy(() -> chatService.acceptChat(chat.getId(), SENDER_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void acceptChat_onAlreadyAcceptedChat_throwsChatNotAccepted() {
        Chat chat = chat(SENDER_ID, RECEIVER_ID, ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        assertThatThrownBy(() -> chatService.acceptChat(chat.getId(), RECEIVER_ID))
                .isInstanceOf(ChatNotAcceptedException.class);
    }

    @Test
    void rejectChat_byRecipient_marksRejectedAndNotifiesSender() {
        Chat chat = chat(SENDER_ID, RECEIVER_ID, ChatStatus.PENDING, 1);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        chatService.rejectChat(chat.getId(), RECEIVER_ID);

        assertThat(chat.getStatus()).isEqualTo(ChatStatus.REJECTED);
        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, times(1)).sendNotification(eq(SENDER_ID), notification.capture());
        assertThat(notification.getValue().getType()).isEqualTo(NotificationType.CHAT_REQUEST_REJECTED);
    }

    @Test
    void toggleFavorite_bySender_flipsSenderFavoriteOnly() {
        Chat chat = chat(SENDER_ID, RECEIVER_ID, ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        boolean result = chatService.toggleFavorite(chat.getId(), SENDER_ID);

        assertThat(result).isTrue();
        assertThat(chat.isSenderFavorite()).isTrue();
        assertThat(chat.isRecipientFavorite()).isFalse();
        verify(chatRepository).save(chat);
    }

    @Test
    void toggleFavorite_byRecipient_flipsRecipientFavoriteOnly() {
        Chat chat = chat(SENDER_ID, RECEIVER_ID, ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        chatService.toggleFavorite(chat.getId(), RECEIVER_ID);
        boolean result = chatService.toggleFavorite(chat.getId(), RECEIVER_ID);

        assertThat(result).isFalse();
        assertThat(chat.isRecipientFavorite()).isFalse();
        assertThat(chat.isSenderFavorite()).isFalse();
    }

    @Test
    void toggleFavorite_byNonParticipant_throwsAccessDenied() {
        Chat chat = chat(SENDER_ID, RECEIVER_ID, ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        assertThatThrownBy(() -> chatService.toggleFavorite(chat.getId(), "someone-else"))
                .isInstanceOf(AccessDeniedException.class);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void toggleArchive_setsArchivedForSenderOnly() {
        Chat chat = chat(SENDER_ID, RECEIVER_ID, ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        boolean result = chatService.toggleArchive(chat.getId(), SENDER_ID);

        assertThat(result).isTrue();
        assertThat(chat.isSenderArchived()).isTrue();
        assertThat(chat.isRecipientArchived()).isFalse();
        verify(chatRepository).save(chat);
    }

    @Test
    void toggleArchive_byRecipient_flipsRecipientArchivedOnly() {
        Chat chat = chat(SENDER_ID, RECEIVER_ID, ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        chatService.toggleArchive(chat.getId(), RECEIVER_ID);
        boolean result = chatService.toggleArchive(chat.getId(), RECEIVER_ID);

        assertThat(result).isFalse();
        assertThat(chat.isRecipientArchived()).isFalse();
        assertThat(chat.isSenderArchived()).isFalse();
    }

    @Test
    void toggleArchive_nonParticipant_throwsAccessDenied() {
        Chat chat = chat(SENDER_ID, RECEIVER_ID, ChatStatus.ACCEPTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        assertThatThrownBy(() -> chatService.toggleArchive(chat.getId(), "someone-else"))
                .isInstanceOf(AccessDeniedException.class);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void acceptChat_unknownChat_throwsEntityNotFound() {
        when(chatRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.acceptChat("missing", RECEIVER_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setFirstName("First-" + id);
        user.setLastName("Last-" + id);
        return user;
    }

    private static Chat chat(String senderId, String receiverId, ChatStatus status, int pendingMessageCount) {
        Chat chat = new Chat();
        chat.setId("chat-" + senderId + "-" + receiverId);
        chat.setSender(user(senderId));
        chat.setRecipient(user(receiverId));
        chat.setStatus(status);
        chat.setPendingMessageCount(pendingMessageCount);
        return chat;
    }
}
