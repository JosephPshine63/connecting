package dev.pioruocco.wacchat.call;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class ChatValidationClientTest {

    @Test
    void fallback_failsClosed_denyingTheCall() throws Exception {
        // No Resilience4j aspect is active in this plain unit test (that requires a Spring
        // context) — this exercises the fallback method directly, the same pattern used for
        // GeminiClient's fallback in the backend. Unlike SessionValidationClient's session-lock
        // fallback (fails open), chat membership is a security boundary, so this must deny.
        ChatValidationClient client = new ChatValidationClient(WebClient.builder().build(), 2000L);

        var method = ChatValidationClient.class.getDeclaredMethod("isAcceptedFallback", String.class, String.class, Throwable.class);
        method.setAccessible(true);

        Object result = method.invoke(client, "user-1", "peer-1", new RuntimeException("backend down"));

        assertThat(result).isEqualTo(false);
    }
}
