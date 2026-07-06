package dev.pioruocco.wacchat.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = "${application.notification.queue}")
    public void onNotificationEvent(NotificationEvent event) {
        log.info("Pushing notification to {}: type={} chatId={}", event.userId(), event.notification().getType(), event.notification().getChatId());
        // Destination must resolve (after the /user/{id} prefix is stripped) to something
        // starting with /queue or /topic — those are the only prefixes registered with
        // enableStompBrokerRelay in WebSocketConfig, and RabbitMQ's STOMP plugin only
        // recognizes /queue|/topic|/exchange destinations. A bare "/chat" here silently
        // never reaches the relay at all (no error, no forward — just dropped).
        messagingTemplate.convertAndSendToUser(event.userId(), "/queue/chat", event.notification());
    }
}
