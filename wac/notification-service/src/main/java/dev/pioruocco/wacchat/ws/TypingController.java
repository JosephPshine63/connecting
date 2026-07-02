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
 * level as the rest of the realtime layer, so receiverId is taken from the client
 * payload as-is rather than round-tripping to backend to check chat membership.
 */
@Controller
@RequiredArgsConstructor
public class TypingController {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload TypingRequest request, Principal principal) {
        if (principal == null || request.chatId() == null || request.receiverId() == null) {
            return;
        }
        Notification notification = Notification.builder()
                .chatId(request.chatId())
                .senderId(principal.getName())
                .receiverId(request.receiverId())
                .type(request.typing() ? NotificationType.TYPING_START : NotificationType.TYPING_STOP)
                .build();
        messagingTemplate.convertAndSendToUser(request.receiverId(), "/chat", notification);
    }
}
