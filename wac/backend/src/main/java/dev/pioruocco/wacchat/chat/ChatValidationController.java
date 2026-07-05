package dev.pioruocco.wacchat.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by call-service before allowing a call invite/answer, to check that the two
 * users share an ACCEPTED chat. Unlike SessionValidationController's fail-open session
 * lock (a UX nicety), this is a real security boundary — the caller (ChatValidationClient
 * in call-service) must fail closed (deny) if this endpoint is unreachable. Guarded by
 * InternalAuthFilter, not JWT.
 */
@RestController
@RequestMapping("/api/v1/internal/chats")
@RequiredArgsConstructor
public class ChatValidationController {

    private final ChatRepository chatRepository;

    @PostMapping("/validate")
    public ChatValidationResponse validate(@RequestBody ChatValidationRequest request) {
        boolean accepted = chatRepository.findChatByReceiverAndSender(request.userId(), request.peerId())
                .map(chat -> chat.getStatus() == ChatStatus.ACCEPTED)
                .orElse(false);
        return new ChatValidationResponse(accepted);
    }

    public record ChatValidationRequest(String userId, String peerId) {
    }

    public record ChatValidationResponse(boolean accepted) {
    }
}
