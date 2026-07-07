package dev.pioruocco.wacchat.call;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Calls backend's internal push-send endpoint to wake a backgrounded/closed client for an
 * incoming call INVITE. Fails open on backend outage, same as SessionValidationClient — a
 * missed push is a UX degradation, not a security boundary, and WS signaling (which is
 * unaffected either way) is the call's actual delivery path when the client is foreground.
 */
@Service
@Slf4j
public class PushNotificationClient {

    private final WebClient webClient;
    private final long responseTimeoutMs;

    public PushNotificationClient(WebClient backendWebClient,
                                   @Value("${application.backend.response-timeout-ms}") long responseTimeoutMs) {
        this.webClient = backendWebClient;
        this.responseTimeoutMs = responseTimeoutMs;
    }

    @CircuitBreaker(name = "pushNotification", fallbackMethod = "sendFallback")
    @Retry(name = "pushNotification")
    public void send(String userId, String title, String body, String chatId) {
        webClient.post()
                .uri("/api/v1/internal/push/send")
                .bodyValue(new SendPushRequest(userId, title, body, chatId))
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofMillis(responseTimeoutMs));
    }

    @SuppressWarnings("unused")
    private void sendFallback(String userId, String title, String body, String chatId, Throwable t) {
        log.warn("Push notification call to backend failed, failing open for user {}", userId, t);
    }

    private record SendPushRequest(String userId, String title, String body, String chatId) {
    }
}
