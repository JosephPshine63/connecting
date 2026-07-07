package dev.pioruocco.wacchat.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushService {

    private final nl.martijndwars.webpush.PushService webPushService;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final ObjectMapper objectMapper;

    // Checked directly (mirrors BotService.isEnabled()) rather than nullability of the
    // webPushService bean — a Spring @Bean method returning null isn't a reliable signal
    // for constructor-injected, non-Optional-typed dependencies across bean-resolution
    // paths, so PushConfig always builds a real (possibly key-less) instance instead.
    @Value("${application.push.vapid-public-key:}")
    private String vapidPublicKey;

    @Value("${application.push.vapid-private-key:}")
    private String vapidPrivateKey;

    public boolean isEnabled() {
        return !vapidPublicKey.isBlank() && !vapidPrivateKey.isBlank();
    }

    /** Fans out to every subscription this user has (one per browser/device). Best-effort —
     *  a failed push is logged, never surfaced to the caller (see PushDispatcher). */
    public void sendPush(String userId, String title, String body, String chatId) {
        if (!isEnabled()) {
            return;
        }
        for (PushSubscription sub : pushSubscriptionRepository.findByUserId(userId)) {
            try {
                Subscription subscription = new Subscription(sub.getEndpoint(),
                        new Subscription.Keys(sub.getP256dh(), sub.getAuthKey()));
                String payload = objectMapper.writeValueAsString(new PushPayload(title, body, chatId, "/"));
                Notification notification = new Notification(subscription, payload);
                HttpResponse response = webPushService.send(notification);
                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    pushSubscriptionRepository.deleteByEndpoint(sub.getEndpoint());
                    log.info("Pruned expired push subscription (status {}) for user {}", status, userId);
                }
            } catch (Exception e) {
                log.error("Failed to send push notification to user {}", userId, e);
            }
        }
    }

    private record PushPayload(String title, String body, String chatId, String url) {
    }
}
