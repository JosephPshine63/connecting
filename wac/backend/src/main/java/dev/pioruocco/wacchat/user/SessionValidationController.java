package dev.pioruocco.wacchat.user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by notification-service on every STOMP CONNECT to enforce the single-session
 * lock (see SessionGuard) now that the WebSocket layer no longer shares a JVM/DB
 * connection with the backend. Guarded by InternalAuthFilter, not JWT.
 */
@RestController
@RequestMapping("/api/v1/internal/sessions")
@RequiredArgsConstructor
public class SessionValidationController {

    private final UserRepository userRepository;
    private final SessionGuard sessionGuard;

    @PostMapping("/validate")
    public SessionValidationResponse validate(@RequestBody SessionValidationRequest request) {
        boolean conflicting = userRepository.findByPublicId(request.userId())
                .map(user -> sessionGuard.isConflicting(user, request.tabId()))
                .orElse(false);
        return new SessionValidationResponse(conflicting);
    }

    public record SessionValidationRequest(String userId, String tabId) {
    }

    public record SessionValidationResponse(boolean conflicting) {
    }
}
