package dev.pioruocco.wacchat.message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InternalSystemMessageControllerTest {

    @Mock
    private SystemMessageSender systemMessageSender;

    private InternalSystemMessageController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalSystemMessageController(systemMessageSender);
    }

    @Test
    void postSystemMessage_delegatesToSystemMessageSender_withTextType() {
        controller.postSystemMessage(new InternalSystemMessageController.SystemMessageRequest(
                "chat-1", "caller-1", "callee-1", "Chiamata terminata - durata 03:12"));

        verify(systemMessageSender).saveSystemMessage("chat-1", "caller-1", "callee-1",
                "Chiamata terminata - durata 03:12", MessageType.TEXT);
    }
}
