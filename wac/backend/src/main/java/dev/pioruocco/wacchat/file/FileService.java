package dev.pioruocco.wacchat.file;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static java.io.File.separator;
import static java.lang.System.currentTimeMillis;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "svg", "mp4", "mov", "mp3"
    );

    @Value("${application.file.uploads.media-output-path}")
    private String fileUploadPath;

    public String saveFile(
            @Nonnull MultipartFile sourceFile,
            @Nonnull String userId
    ) {
        final String fileUploadSubPath = "users" + separator + userId;
        return uploadFile(sourceFile, fileUploadSubPath);
    }

    private String uploadFile(
            @Nonnull MultipartFile sourceFile,
            @Nonnull String fileUploadSubPath
    ) {
        final String fileExtension = getFileExtension(sourceFile.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
            log.warn("Rejected upload with disallowed extension: {}", fileExtension);
            return null;
        }

        Path basePath = Paths.get(fileUploadPath).normalize().toAbsolutePath();
        Path targetFolder = basePath.resolve(fileUploadSubPath).normalize();

        // Guard: path traversal — ensure target stays within the configured upload directory
        if (!targetFolder.startsWith(basePath)) {
            log.error("Path traversal attempt detected for subpath: {}", fileUploadSubPath);
            return null;
        }

        try {
            Files.createDirectories(targetFolder);
        } catch (IOException e) {
            log.warn("Failed to create target folder: {}", targetFolder);
            return null;
        }

        Path targetPath = targetFolder.resolve(currentTimeMillis() + "." + fileExtension).normalize();
        if (!targetPath.startsWith(basePath)) {
            log.error("Path traversal attempt detected for target file: {}", targetPath);
            return null;
        }

        try {
            Files.write(targetPath, sourceFile.getBytes());
            log.info("File saved to: {}", targetPath);
            return targetPath.toString();
        } catch (IOException e) {
            log.error("File was not saved", e);
        }
        return null;
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
