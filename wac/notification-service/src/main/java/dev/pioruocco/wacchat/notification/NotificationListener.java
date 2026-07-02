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
        log.info("Pushing notification to {} with payload {}", event.userId(), event.notification());
        messagingTemplate.convertAndSendToUser(event.userId(), "/chat", event.notification());
    }
}
