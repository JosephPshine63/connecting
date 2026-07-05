package dev.pioruocco.wacchat.call;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CallSignalListener {

    private final SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = "${application.call.queue}")
    public void onCallSignalEvent(CallSignalEvent event) {
        log.info("Pushing call signal to {} with payload {}", event.toUserId(), event.signal());
        // Same /queue prefix requirement as NotificationListener — enableStompBrokerRelay
        // in WebSocketConfig only forwards /topic and /queue destinations.
        messagingTemplate.convertAndSendToUser(event.toUserId(), "/queue/call", event.signal());
    }
}
