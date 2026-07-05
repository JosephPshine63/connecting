package dev.pioruocco.wacchat.message;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets call-service leave a system message in chat history ("Missed call" / "Call ended")
 * at call end, without call-service owning a DB of its own. Reuses SystemMessageSender,
 * the same mechanism BotService uses to write messages without an Authentication. Guarded
 * by InternalAuthFilter, not JWT.
 */
@RestController
@RequestMapping("/api/v1/internal/messages")
@RequiredArgsConstructor
public class InternalSystemMessageController {

    private final SystemMessageSender systemMessageSender;

    @PostMapping("/system")
    public void postSystemMessage(@RequestBody SystemMessageRequest request) {
        systemMessageSender.saveSystemMessage(request.chatId(), request.senderId(),
                request.receiverId(), request.content(), MessageType.TEXT);
    }

    public record SystemMessageRequest(String chatId, String senderId, String receiverId, String content) {
    }
}
