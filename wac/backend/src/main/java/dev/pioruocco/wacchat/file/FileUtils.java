package dev.pioruocco.wacchat.file;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

@Slf4j
public class FileUtils {

    // Pre-R2-migration messages stored an absolute local disk path under this directory
    // (historically configured as application.file.uploads.media-output-path: ./uploads,
    // now removed since uploads moved to file-service/R2 — kept only for legacy reads).
    private static final String LEGACY_UPLOADS_ROOT = "uploads";

    private FileUtils() {}

    public static List<String> resolveMedia(String mediaFilePathOrUrl) {
        if (StringUtils.isBlank(mediaFilePathOrUrl)) {
            return List.of();
        }
        if (mediaFilePathOrUrl.startsWith("http://") || mediaFilePathOrUrl.startsWith("https://")) {
            return List.of(mediaFilePathOrUrl);
        }
        // Legacy messages stored a local disk path before media moved to R2.
        return readFileAsBase64(mediaFilePathOrUrl);
    }

    public static List<String> readFileAsBase64(String fileUrl) {
        byte[] bytes = readFileFromLocation(fileUrl);
        if (bytes.length == 0) {
            return List.of();
        }
        return List.of(Base64.getEncoder().encodeToString(bytes));
    }

    public static byte[] readFileFromLocation(String fileUrl) {
        if (StringUtils.isBlank(fileUrl)) {
            return new byte[0];
        }
        // Reject paths with traversal sequences before resolving
        if (fileUrl.contains("..")) {
            log.warn("Rejected file read with suspicious path: {}", fileUrl);
            return new byte[0];
        }
        try {
            Path basePath = Paths.get(LEGACY_UPLOADS_ROOT).normalize().toAbsolutePath();
            Path filePath = basePath.resolve(fileUrl).normalize();
            if (!filePath.startsWith(basePath)) {
                log.warn("Rejected file read outside legacy uploads root: {}", fileUrl);
                return new byte[0];
            }
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.warn("No file found at path {}", fileUrl);
        }
        return new byte[0];
    }
}
