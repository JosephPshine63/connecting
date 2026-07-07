package dev.pioruocco.wacchat.ws;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Calls backend's internal chat-validation endpoint (the same one call-service uses
 * before invite/answer) to check the typing ping's chatId/receiverId actually refer to
 * an ACCEPTED DIRECT chat between the two parties. Same trust level as
 * SessionValidationClient: fails open on backend outage, since typing is a UX nicety,
 * not a security boundary (see TypingController's class-level comment) — worst case on
 * a backend outage is a typing ping that shouldn't have been relayed, not a leak of
 * anything more sensitive than "someone is typing."
 */
@Service
@Slf4j
public class TypingValidationClient {

    private final WebClient webClient;
    private final long responseTimeoutMs;

    public TypingValidationClient(WebClient backendWebClient,
                                   @Value("${application.backend.response-timeout-ms}") long responseTimeoutMs) {
        this.webClient = backendWebClient;
        this.responseTimeoutMs = responseTimeoutMs;
    }

    @CircuitBreaker(name = "typingValidation", fallbackMethod = "isAcceptedChatFallback")
    @Retry(name = "typingValidation")
    public boolean isAcceptedChat(String chatId, String userId, String peerId) {
        ValidationResponse response = webClient.post()
                .uri("/api/v1/internal/chats/validate")
                .bodyValue(new ValidationRequest(chatId, userId, peerId))
                .retrieve()
                .bodyToMono(ValidationResponse.class)
                .block(Duration.ofMillis(responseTimeoutMs));
        return response != null && response.accepted();
    }

    @SuppressWarnings("unused")
    private boolean isAcceptedChatFallback(String chatId, String userId, String peerId, Throwable t) {
        log.warn("Chat validation call to backend failed, failing open for chat {}", chatId, t);
        return true;
    }

    private record ValidationRequest(String chatId, String userId, String peerId) {
    }

    private record ValidationResponse(boolean accepted) {
    }
}
