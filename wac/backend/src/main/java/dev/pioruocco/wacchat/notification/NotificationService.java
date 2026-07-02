package dev.pioruocco.wacchat.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${application.notification.exchange}")
    private String exchangeName;

    @Value("${application.notification.routing-key}")
    private String routingKey;

    public void sendNotification(String userId, Notification notification) {
        log.info("Publishing notification event for {} with payload {}", userId, notification);
        rabbitTemplate.convertAndSend(exchangeName, routingKey, new NotificationEvent(userId, notification));
    }
}
