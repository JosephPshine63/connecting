package dev.pioruocco.wacchat.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatValidationControllerTest {

    private static final String USER_ID = "user-1";
    private static final String PEER_ID = "peer-1";

    @Mock
    private ChatRepository chatRepository;
    @Mock
    private Chat chat;

    private ChatValidationController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatValidationController(chatRepository);
    }

    @Test
    void validate_acceptedChat_returnsAccepted() {
        when(chatRepository.findChatByReceiverAndSender(USER_ID, PEER_ID)).thenReturn(Optional.of(chat));
        when(chat.getStatus()).thenReturn(ChatStatus.ACCEPTED);

        ChatValidationController.ChatValidationResponse response =
                controller.validate(new ChatValidationController.ChatValidationRequest(USER_ID, PEER_ID));

        assertThat(response.accepted()).isTrue();
    }

    @Test
    void validate_pendingChat_returnsNotAccepted() {
        when(chatRepository.findChatByReceiverAndSender(USER_ID, PEER_ID)).thenReturn(Optional.of(chat));
        when(chat.getStatus()).thenReturn(ChatStatus.PENDING);

        ChatValidationController.ChatValidationResponse response =
                controller.validate(new ChatValidationController.ChatValidationRequest(USER_ID, PEER_ID));

        assertThat(response.accepted()).isFalse();
    }

    @Test
    void validate_noChatExists_returnsNotAccepted() {
        when(chatRepository.findChatByReceiverAndSender(USER_ID, PEER_ID)).thenReturn(Optional.empty());

        ChatValidationController.ChatValidationResponse response =
                controller.validate(new ChatValidationController.ChatValidationRequest(USER_ID, PEER_ID));

        assertThat(response.accepted()).isFalse();
    }
}
