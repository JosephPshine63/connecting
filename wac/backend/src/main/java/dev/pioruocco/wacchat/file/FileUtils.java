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

    private FileUtils() {}

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
            Path filePath = Paths.get(fileUrl).normalize();
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.warn("No file found at path {}", fileUrl);
        }
        return new byte[0];
    }
}
