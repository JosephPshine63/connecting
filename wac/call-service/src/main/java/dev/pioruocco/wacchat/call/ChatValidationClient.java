package dev.pioruocco.wacchat.call;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * Calls backend's internal /chats/validate endpoint. Unlike notification-service's
 * SessionValidationClient (a UX-only session lock, which fails open), chat membership is
 * a real security boundary — a caller must not be able to ring a contact they don't have
 * an ACCEPTED chat with. The fallback below therefore fails closed (denies the call).
 */
@Service
@Slf4j
public class ChatValidationClient {

    private final WebClient webClient;
    private final long responseTimeoutMs;

    public ChatValidationClient(WebClient backendWebClient,
                                 @Value("${application.backend.response-timeout-ms}") long responseTimeoutMs) {
        this.webClient = backendWebClient;
        this.responseTimeoutMs = responseTimeoutMs;
    }

    @CircuitBreaker(name = "chatValidation", fallbackMethod = "isAcceptedFallback")
    @Retry(name = "chatValidation")
    public boolean isAccepted(String chatId, String userId, String peerId) {
        ValidationResponse response = webClient.post()
                .uri("/api/v1/internal/chats/validate")
                .bodyValue(new ValidationRequest(chatId, userId, peerId))
                .retrieve()
                .bodyToMono(ValidationResponse.class)
                .block(Duration.ofMillis(responseTimeoutMs));
        return response != null && response.accepted();
    }

    @SuppressWarnings("unused")
    private boolean isAcceptedFallback(String chatId, String userId, String peerId, Throwable t) {
        log.warn("Chat validation call to backend failed, failing closed (denying call) for user {}", userId, t);
        return false;
    }

    @CircuitBreaker(name = "chatValidation", fallbackMethod = "isGroupCallAllowedFallback")
    @Retry(name = "chatValidation")
    public boolean isGroupCallAllowed(String chatId, String callerId, List<String> inviteeIds) {
        GroupCallValidationResponse response = webClient.post()
                .uri("/api/v1/internal/chats/validate-group")
                .bodyValue(new GroupCallValidationRequest(chatId, callerId, inviteeIds))
                .retrieve()
                .bodyToMono(GroupCallValidationResponse.class)
                .block(Duration.ofMillis(responseTimeoutMs));
        return response != null && response.accepted();
    }

    @SuppressWarnings("unused")
    private boolean isGroupCallAllowedFallback(String chatId, String callerId, List<String> inviteeIds, Throwable t) {
        log.warn("Group chat validation call to backend failed, failing closed (denying call) for caller {}", callerId, t);
        return false;
    }

    private record ValidationRequest(String chatId, String userId, String peerId) {
    }

    private record ValidationResponse(boolean accepted) {
    }

    private record GroupCallValidationRequest(String chatId, String callerId, List<String> inviteeIds) {
    }

    private record GroupCallValidationResponse(boolean accepted) {
    }
}
