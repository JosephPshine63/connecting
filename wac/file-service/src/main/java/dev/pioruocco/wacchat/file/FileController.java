package dev.pioruocco.wacchat.file;

import dev.pioruocco.wacchat.file.dto.UploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class FileController {

    private final R2StorageService r2StorageService;

    @PostMapping(value = "/api/v1/files/avatars/{userId}", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse uploadAvatar(@PathVariable String userId, @RequestPart("file") MultipartFile file,
                                        Authentication authentication) {
        assertOwner(userId, authentication);
        return new UploadResponse(r2StorageService.uploadAvatar(file, userId));
    }

    @PostMapping(value = "/api/v1/files/messages/{userId}/media", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse uploadMessageMedia(@PathVariable String userId, @RequestPart("file") MultipartFile file,
                                              Authentication authentication) {
        assertOwner(userId, authentication);
        return new UploadResponse(r2StorageService.uploadMessageMedia(file, userId));
    }

    @DeleteMapping("/api/v1/files/objects")
    public ResponseEntity<Void> deleteObject(@RequestParam String url, Authentication authentication) {
        r2StorageService.deleteObject(url, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    private void assertOwner(String userId, Authentication authentication) {
        if (!userId.equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot act on another user's files");
        }
    }
}
