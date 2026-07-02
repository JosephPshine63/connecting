package dev.pioruocco.wacchat.bot;

import dev.pioruocco.wacchat.bot.GeminiClient.GeminiContent;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiClientTest {

    private MockWebServer server;
    private GeminiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        client = new GeminiClient(webClient, "gemini-2.0-flash", 2000L);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void generateReply_returnsTextOnSuccess() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Ciao!\"}]}}]}"));

        String reply = client.generateReply(List.of(GeminiContent.of("user", "ciao")));

        assertThat(reply).isEqualTo("Ciao!");
        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/v1beta/models/gemini-2.0-flash:generateContent");
    }

    @Test
    void generateReply_emptyCandidatesReturnsNull() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"candidates\":[]}"));

        String reply = client.generateReply(List.of(GeminiContent.of("user", "ciao")));

        assertThat(reply).isNull();
    }

    @Test
    void generateReply_serverErrorPropagatesAsWebClientException() {
        server.enqueue(new MockResponse().setResponseCode(500));

        // No Resilience4j aspect is active in this plain unit test (that requires a Spring
        // context), so a 5xx propagates as the raw WebClient exception here — the fallback
        // below is what turns this into a null reply when wired through Spring.
        assertThatThrownBy(() -> client.generateReply(List.of(GeminiContent.of("user", "ciao"))))
                .isNotNull();
    }

    @Test
    void fallback_returnsNull() throws Exception {
        var method = GeminiClient.class.getDeclaredMethod("generateReplyFallback", List.class, Throwable.class);
        method.setAccessible(true);

        Object result = method.invoke(client, List.of(), new RuntimeException("timeout"));

        assertThat(result).isNull();
    }
}
