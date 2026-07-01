package dev.pioruocco.wacchat.file;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileServiceClientTest {

    private MockWebServer server;
    private FileServiceClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        client = new FileServiceClient(webClient, 2000L);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void uploadAvatar_returnsUrlOnSuccess() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"url\":\"https://r2.example.com/avatars/user-1/1.png\"}"));

        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "content".getBytes());
        String url = client.uploadAvatar(file, "user-1");

        assertThat(url).isEqualTo("https://r2.example.com/avatars/user-1/1.png");
        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/api/v1/files/avatars/user-1");
    }

    @Test
    void uploadAvatar_propagatesBadRequestAsIs() {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("Unsupported file type: exe"));

        MockMultipartFile file = new MockMultipartFile("file", "avatar.exe", "application/octet-stream", "content".getBytes());

        assertThatThrownBy(() -> client.uploadAvatar(file, "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void uploadAvatar_serverErrorPropagatesAsWebClientException() {
        server.enqueue(new MockResponse().setResponseCode(500));

        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "content".getBytes());

        // No Resilience4j aspect is active in this plain unit test (that requires a Spring
        // context), so a 5xx propagates as the raw WebClient exception here. Translating *this*
        // failure mode into a 503 is the job of the fallback method, exercised directly below.
        assertThatThrownBy(() -> client.uploadAvatar(file, "user-1"))
                .isNotInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deleteObject_succeedsOnNoContent() {
        server.enqueue(new MockResponse().setResponseCode(204));

        client.deleteObject("https://r2.example.com/avatars/user-1/1.png");
    }

    @Test
    void fallback_translatesGenericFailureTo503() throws Exception {
        var method = FileServiceClient.class.getDeclaredMethod("translateFailure", Throwable.class);
        method.setAccessible(true);
        Object result = method.invoke(client, new RuntimeException("connection refused"));

        assertThat(result).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void fallback_rethrowsBadRequestUnchanged() throws Exception {
        var method = FileServiceClient.class.getDeclaredMethod("translateFailure", Throwable.class);
        method.setAccessible(true);
        ResponseStatusException original = new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad file");

        Object result = method.invoke(client, original);

        assertThat(result).isSameAs(original);
    }
}
