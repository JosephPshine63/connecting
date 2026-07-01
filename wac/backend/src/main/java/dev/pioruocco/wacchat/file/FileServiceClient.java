package dev.pioruocco.wacchat.file;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

/**
 * Replaces the in-process {@code R2StorageService} call with an HTTP call to the standalone
 * file-service, guarded by Resilience4j so a down/slow file-service degrades to a clear 503
 * instead of hanging every avatar/media upload request.
 */
@Service
@Slf4j
public class FileServiceClient {

    private final WebClient webClient;
    private final long responseTimeoutMs;

    public FileServiceClient(WebClient fileServiceWebClient,
                              @Value("${application.file-service.response-timeout-ms}") long responseTimeoutMs) {
        this.webClient = fileServiceWebClient;
        this.responseTimeoutMs = responseTimeoutMs;
    }

    @CircuitBreaker(name = "fileService", fallbackMethod = "uploadFallback")
    @Retry(name = "fileService")
    public String uploadAvatar(@Nonnull MultipartFile file, @Nonnull String userId) {
        return upload("/api/v1/files/avatars/" + userId, file);
    }

    @CircuitBreaker(name = "fileService", fallbackMethod = "uploadFallback")
    @Retry(name = "fileService")
    public String uploadMessageMedia(@Nonnull MultipartFile file, @Nonnull String userId) {
        return upload("/api/v1/files/messages/" + userId + "/media", file);
    }

    @CircuitBreaker(name = "fileService", fallbackMethod = "deleteFallback")
    @Retry(name = "fileService")
    public void deleteAvatar(@Nonnull String avatarUrl) {
        deleteObject(avatarUrl);
    }

    @CircuitBreaker(name = "fileService", fallbackMethod = "deleteFallback")
    @Retry(name = "fileService")
    public void deleteObject(@Nonnull String publicUrl) {
        try {
            webClient.method(HttpMethod.DELETE)
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/files/objects").queryParam("url", publicUrl).build())
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofMillis(responseTimeoutMs));
        } catch (WebClientResponseException wcre) {
            throw translateClientError(wcre);
        }
    }

    private String upload(String uri, MultipartFile file) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        try {
            byte[] bytes = file.getBytes();
            builder.part("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            }).contentType(MediaType.parseMediaType(
                    file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try {
            UploadResponseDto response = webClient.post()
                    .uri(uri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(UploadResponseDto.class)
                    .block(Duration.ofMillis(responseTimeoutMs));

            return response != null ? response.url() : null;
        } catch (WebClientResponseException wcre) {
            throw translateClientError(wcre);
        }
    }

    /** Bad requests (invalid extension, oversized file) are a genuine client error, not a
     * file-service outage — surface them as-is instead of retrying/tripping the breaker. */
    private ResponseStatusException translateClientError(WebClientResponseException wcre) {
        if (wcre.getStatusCode().equals(HttpStatus.BAD_REQUEST)) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, wcre.getResponseBodyAsString());
        }
        throw wcre;
    }

    @SuppressWarnings("unused")
    private String uploadFallback(MultipartFile file, String userId, Throwable t) {
        throw translateFailure(t);
    }

    @SuppressWarnings("unused")
    private void deleteFallback(String publicUrl, Throwable t) {
        if (t instanceof ResponseStatusException rse) {
            throw rse;
        }
        log.warn("file-service delete call failed, ignoring: {}", publicUrl, t);
    }

    private ResponseStatusException translateFailure(Throwable t) {
        if (t instanceof ResponseStatusException rse) {
            return rse;
        }
        log.error("file-service call failed", t);
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "File storage is temporarily unavailable, please try again later");
    }

    private record UploadResponseDto(String url) {
    }
}
