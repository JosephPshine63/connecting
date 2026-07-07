package dev.pioruocco.wacchat.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Separate bean from PushService so @Async actually applies — calling an @Async method
 * on `this` from within the same class bypasses the Spring AOP proxy and runs
 * synchronously (classic self-invocation gotcha). Kept intentionally thin.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PushDispatcher {

    private final PushService pushService;

    @Async("pushExecutor")
    public void dispatch(String userId, String title, String body, String chatId) {
        try {
            pushService.sendPush(userId, title, body, chatId);
        } catch (Exception e) {
            log.error("Push dispatch failed for user {}", userId, e);
        }
    }
}
