package dev.pioruocco.wacchat.ws;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Calls backend's internal session-validation endpoint to enforce the single-session
 * lock on STOMP CONNECT, now that this check can no longer be a local DB read (that
 * logic — SessionGuard — stayed in backend). Fails open on backend outage: a WS
 * connection is more valuable than a strict lock, and the lock is a UX nicety, not a
 * security boundary (JWT auth is unaffected either way).
 */
@Service
@Slf4j
public class SessionValidationClient {

    private final WebClient webClient;
    private final long responseTimeoutMs;

    public SessionValidationClient(WebClient backendWebClient,
                                    @Value("${application.backend.response-timeout-ms}") long responseTimeoutMs) {
        this.webClient = backendWebClient;
        this.responseTimeoutMs = responseTimeoutMs;
    }

    @CircuitBreaker(name = "sessionValidation", fallbackMethod = "isConflictingFallback")
    @Retry(name = "sessionValidation")
    public boolean isConflicting(String userId, String tabId) {
        ValidationResponse response = webClient.post()
                .uri("/api/v1/internal/sessions/validate")
                .bodyValue(new ValidationRequest(userId, tabId))
                .retrieve()
                .bodyToMono(ValidationResponse.class)
                .block(Duration.ofMillis(responseTimeoutMs));
        return response != null && response.conflicting();
    }

    @SuppressWarnings("unused")
    private boolean isConflictingFallback(String userId, String tabId, Throwable t) {
        log.warn("Session validation call to backend failed, failing open for user {}", userId, t);
        return false;
    }

    private record ValidationRequest(String userId, String tabId) {
    }

    private record ValidationResponse(boolean conflicting) {
    }
}
