package dev.pioruocco.wacchat.ws;

import dev.pioruocco.wacchat.notification.Notification;
import dev.pioruocco.wacchat.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Relays live "user is typing" pings between two chat participants. Fire-and-forget,
 * client-throttled, never persisted — same "UX nicety, not a security boundary" trust
 * level as the rest of the realtime layer, so a failed/timed-out validation call still
 * lets the ping through (TypingValidationClient fails open) rather than ever blocking
 * delivery. The validation call itself exists only to stop a client from spoofing
 * receiverId to ping an arbitrary user it has no chat with.
 */
@Controller
@RequiredArgsConstructor
public class TypingController {

    private final SimpMessagingTemplate messagingTemplate;
    private final TypingValidationClient typingValidationClient;

    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload TypingRequest request, Principal principal) {
        if (principal == null || request.chatId() == null || request.receiverId() == null) {
            return;
        }
        if (!typingValidationClient.isAcceptedChat(request.chatId(), principal.getName(), request.receiverId())) {
            return;
        }
        Notification notification = Notification.builder()
                .chatId(request.chatId())
                .senderId(principal.getName())
                .receiverId(request.receiverId())
                .type(request.typing() ? NotificationType.TYPING_START : NotificationType.TYPING_STOP)
                .build();
        messagingTemplate.convertAndSendToUser(request.receiverId(), "/queue/chat", notification);
    }
}
