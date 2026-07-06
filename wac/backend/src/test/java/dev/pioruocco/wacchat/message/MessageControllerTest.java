package dev.pioruocco.wacchat.message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    @Mock
    private MessageService messageService;
    @Mock
    private Authentication authentication;

    private MessageController controller;

    @BeforeEach
    void setUp() {
        controller = new MessageController(messageService);
    }

    @Test
    void editMessage_delegatesToService() {
        EditMessageRequest request = new EditMessageRequest("updated");
        MessageResponse expected = MessageResponse.builder().id(5L).content("updated").build();
        when(messageService.editMessage(5L, request, authentication)).thenReturn(expected);

        MessageResponse response = controller.editMessage(5L, request, authentication);

        assertThat(response).isEqualTo(expected);
        verify(messageService).editMessage(5L, request, authentication);
    }

    @Test
    void deleteMessage_delegatesToService() {
        controller.deleteMessage(5L, authentication);

        verify(messageService).deleteMessage(5L, authentication);
    }
}
