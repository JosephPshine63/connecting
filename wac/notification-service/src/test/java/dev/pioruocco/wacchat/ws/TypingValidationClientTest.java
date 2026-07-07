package dev.pioruocco.wacchat.ws;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors PushNotificationClientTest/SessionValidationClient's own test bar: no
 * MockWebServer in this module, so the fail-open fallback is exercised directly.
 */
class TypingValidationClientTest {

    @Test
    void isAcceptedChatFallback_failsOpenInsteadOfBlockingTheTypingPing() {
        TypingValidationClient client = new TypingValidationClient(WebClient.create("http://localhost"), 2000L);

        boolean result = ReflectionTestUtils.invokeMethod(
                client, "isAcceptedChatFallback", "chat-1", "user-1", "user-2", new RuntimeException("backend down"));

        assertThat(result).isTrue();
    }
}
