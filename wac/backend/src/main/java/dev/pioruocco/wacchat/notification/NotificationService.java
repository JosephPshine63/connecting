package dev.pioruocco.wacchat.notification;

import dev.pioruocco.wacchat.push.PushDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    // Only types worth waking a backgrounded/closed client for — status-only types (SEEN,
    // AVATAR_UPDATED, MESSAGE_EDITED/DELETED, REACTION_*, CHAT_REQUEST_ACCEPTED/REJECTED)
    // are foreground-UI enrichments, not worth an OS-level push.
    private static final Set<NotificationType> PUSH_ELIGIBLE_TYPES = EnumSet.of(
            NotificationType.MESSAGE, NotificationType.IMAGE, NotificationType.VIDEO, NotificationType.AUDIO,
            NotificationType.CHAT_REQUEST, NotificationType.GROUP_ADDED);

    private final RabbitTemplate rabbitTemplate;
    private final PushDispatcher pushDispatcher;

    @Value("${application.notification.exchange}")
    private String exchangeName;

    @Value("${application.notification.routing-key}")
    private String routingKey;

    public void sendNotification(String userId, Notification notification) {
        log.info("Publishing notification event for {} with payload {}", userId, notification);
        rabbitTemplate.convertAndSend(exchangeName, routingKey, new NotificationEvent(userId, notification));

        if (PUSH_ELIGIBLE_TYPES.contains(notification.getType())) {
            pushDispatcher.dispatch(userId, buildPushTitle(notification), buildPushBody(notification), notification.getChatId());
        }
    }

    private String buildPushTitle(Notification notification) {
        String chatName = notification.getChatName();
        return (chatName != null && !chatName.isBlank()) ? chatName : "Nuovo messaggio";
    }

    private String buildPushBody(Notification notification) {
        return switch (notification.getType()) {
            case IMAGE, VIDEO, AUDIO -> "Ti ha inviato un file multimediale";
            case CHAT_REQUEST -> "Vuole iniziare una chat con te";
            case GROUP_ADDED -> "Ti ha aggiunto a un gruppo";
            default -> notification.getContent() != null ? notification.getContent() : "";
        };
    }
}
