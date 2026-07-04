package dev.pioruocco.wacchat.message;

import dev.pioruocco.wacchat.bot.BotService;
import dev.pioruocco.wacchat.chat.Chat;
import dev.pioruocco.wacchat.chat.ChatNotAcceptedException;
import dev.pioruocco.wacchat.chat.ChatRepository;
import dev.pioruocco.wacchat.chat.ChatRequestLimitExceededException;
import dev.pioruocco.wacchat.chat.ChatStatus;
import dev.pioruocco.wacchat.file.FileServiceClient;
import dev.pioruocco.wacchat.notification.NotificationService;
import dev.pioruocco.wacchat.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private Authentication authentication;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(
                messageRepository, chatRepository, mapper, notificationService, fileServiceClient, botService);
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
    void saveMessage_rejectedChat_throwsChatNotAcceptedForEitherParty() {
        Chat chat = chat(ChatStatus.REJECTED, 0);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(authentication.getName()).thenReturn(REQUESTER_ID);

        assertThatThrownBy(() -> messageService.saveMessage(request(chat.getId()), authentication))
                .isInstanceOf(ChatNotAcceptedException.class);
    }

    private static MessageRequest request(String chatId) {
        return MessageRequest.builder()
                .chatId(chatId)
                .content("hello")
                .type(MessageType.TEXT)
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
