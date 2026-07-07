package dev.pioruocco.wacchat.push;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by notification-service's CallSignalListener for an incoming call INVITE (the
 * only call signal worth waking a backgrounded/closed client for). Guarded by
 * InternalAuthFilter, not JWT — same family as /api/v1/internal/sessions/validate and
 * /api/v1/internal/chats/validate.
 */
@RestController
@RequestMapping("/api/v1/internal/push")
@RequiredArgsConstructor
public class PushInternalController {

    private final PushDispatcher pushDispatcher;

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void send(@RequestBody SendPushRequest request) {
        pushDispatcher.dispatch(request.userId(), request.title(), request.body(), request.chatId());
    }

    public record SendPushRequest(String userId, String title, String body, String chatId) {
    }
}
