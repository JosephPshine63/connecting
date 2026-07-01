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
import java.util.Set;

import static java.lang.System.currentTimeMillis;

@Service
@Slf4j
@RequiredArgsConstructor
public class R2StorageService {

    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_AVATAR_SIZE_BYTES = 5L * 1024 * 1024;

    private static final Set<String> ALLOWED_MESSAGE_MEDIA_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif", "mp4", "mov", "mp3", "wav", "ogg", "m4a"
    );
    private static final long MAX_MESSAGE_MEDIA_SIZE_BYTES = 50L * 1024 * 1024;

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

    public void deleteAvatar(@Nonnull String avatarUrl) {
        deleteObject(avatarUrl);
    }

    public void deleteObject(@Nonnull String publicUrl) {
        String prefix = publicBaseUrl + "/";
        if (!publicUrl.startsWith(prefix)) {
            log.warn("URL does not match configured public base URL, skipping delete: {}", publicUrl);
            return;
        }
        String key = publicUrl.substring(prefix.length());
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

        String key = keyPrefix + "/" + currentTimeMillis() + "." + extension;
        try {
            r2Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes()));
        } catch (IOException | S3Exception e) {
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
