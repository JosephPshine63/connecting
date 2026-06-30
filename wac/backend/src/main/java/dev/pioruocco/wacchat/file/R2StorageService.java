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

    private final S3Client r2Client;

    @Value("${application.r2.bucket-name}")
    private String bucketName;

    @Value("${application.r2.public-base-url}")
    private String publicBaseUrl;

    public String uploadAvatar(@Nonnull MultipartFile file, @Nonnull String userId) {
        String extension = getFileExtension(file.getOriginalFilename());
        if (!ALLOWED_AVATAR_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image type: " + extension);
        }
        if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image exceeds the 5MB limit");
        }

        String key = "avatars/" + userId + "/" + currentTimeMillis() + "." + extension;
        try {
            r2Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes()));
        } catch (IOException | S3Exception e) {
            log.error("Failed to upload avatar to R2 for user {}", userId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not upload avatar");
        }
        return publicBaseUrl + "/" + key;
    }

    public void deleteAvatar(@Nonnull String avatarUrl) {
        String prefix = publicBaseUrl + "/";
        if (!avatarUrl.startsWith(prefix)) {
            log.warn("Avatar URL does not match configured public base URL, skipping delete: {}", avatarUrl);
            return;
        }
        String key = avatarUrl.substring(prefix.length());
        try {
            r2Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            log.warn("Failed to delete old avatar from R2: {}", key, e);
        }
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
