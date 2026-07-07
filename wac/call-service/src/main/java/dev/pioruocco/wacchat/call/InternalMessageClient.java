package dev.pioruocco.wacchat.call;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Leaves a system message in chat history at call end ("Missed call" / "Call ended -
 * duration mm:ss"), via backend's internal /messages/system endpoint. Best-effort and
 * fire-and-forget: the call has already ended by the time this is called, so a failure
 * here must never propagate back to the caller — it only means the chat history misses
 * an entry, not that anything about the call itself failed.
 */
@Service
@Slf4j
public class InternalMessageClient {

    private final WebClient webClient;

    public InternalMessageClient(WebClient backendWebClient) {
        this.webClient = backendWebClient;
    }

    public void sendSystemMessage(String chatId, String senderId, String receiverId, String content) {
        webClient.post()
                .uri("/api/v1/internal/messages/system")
                .bodyValue(new SystemMessageRequest(chatId, senderId, receiverId, content))
                .retrieve()
                .toBodilessEntity()
                .onErrorResume(e -> {
                    log.warn("Failed to leave system message for call in chat {}: {}", chatId, e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    public void sendGroupSystemMessage(String chatId, String senderId, String content) {
        webClient.post()
                .uri("/api/v1/internal/messages/system-group")
                .bodyValue(new GroupSystemMessageRequest(chatId, senderId, content))
                .retrieve()
                .toBodilessEntity()
                .onErrorResume(e -> {
                    log.warn("Failed to leave group system message for call in chat {}: {}", chatId, e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    private record SystemMessageRequest(String chatId, String senderId, String receiverId, String content) {
    }

    private record GroupSystemMessageRequest(String chatId, String senderId, String content) {
    }
}
