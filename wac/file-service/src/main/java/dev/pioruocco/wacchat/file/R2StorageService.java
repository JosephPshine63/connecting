package dev.pioruocco.wacchat.file;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class R2StorageService {

    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_AVATAR_SIZE_BYTES = 5L * 1024 * 1024;

    private static final Set<String> ALLOWED_MESSAGE_MEDIA_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif", "mp4", "mov", "mp3", "wav", "ogg", "m4a", "webm"
    );
    private static final long MAX_MESSAGE_MEDIA_SIZE_BYTES = 50L * 1024 * 1024;

    // Server-side canonical MIME per extension — never trust the client-supplied
    // multipart Content-Type, which is independent of the actual bytes and would
    // otherwise let a magic-byte-valid "polyglot" file be served back publicly
    // with an attacker-chosen Content-Type (e.g. text/html).
    private static final Map<String, String> EXTENSION_TO_CONTENT_TYPE = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("webm", "video/webm"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("m4a", "audio/mp4")
    );

    private final S3Client r2Client;

    @Value("${application.r2.bucket-name}")
    private String bucketName;

    @Value("${application.r2.public-base-url}")
    private String publicBaseUrl;

    public String uploadAvatar(@Nonnull MultipartFile file, @Nonnull String userId) {
        return upload(file, "avatars/" + userId, ALLOWED_AVATAR_EXTENSIONS, MAX_AVATAR_SIZE_BYTES);
    }

    public String uploadMessageMedia(@Nonnull MultipartFile file, @Nonnull String userId) {
        return upload(file, "messages/" + userId, ALLOWED_MESSAGE_MEDIA_EXTENSIONS, MAX_MESSAGE_MEDIA_SIZE_BYTES);
    }

    public void deleteObject(@Nonnull String publicUrl, @Nonnull String requesterId) {
        String prefix = publicBaseUrl + "/";
        if (!publicUrl.startsWith(prefix)) {
            log.warn("URL does not match configured public base URL, skipping delete: {}", publicUrl);
            return;
        }
        String key = publicUrl.substring(prefix.length());
        String[] segments = key.split("/");
        if (segments.length < 2 || !segments[1].equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete another user's file");
        }
        try {
            r2Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            log.warn("Failed to delete object from R2: {}", key, e);
        }
    }

    private String upload(MultipartFile file, String keyPrefix, Set<String> allowedExtensions, long maxSizeBytes) {
        String extension = getFileExtension(file.getOriginalFilename());
        if (!allowedExtensions.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type: " + extension);
        }
        if (file.getSize() > maxSizeBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds the " + (maxSizeBytes / (1024 * 1024)) + "MB limit");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded file");
        }
        MagicByteValidator.validate(extension, content);

        String key = keyPrefix + "/" + UUID.randomUUID() + "." + extension;
        try {
            r2Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(EXTENSION_TO_CONTENT_TYPE.getOrDefault(extension, "application/octet-stream"))
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception e) {
            log.error("Failed to upload file to R2 at key {}", key, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not upload file");
        }
        return publicBaseUrl + "/" + key;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }
}
