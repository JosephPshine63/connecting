package dev.pioruocco.wacchat.user;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers(Authentication authentication) {
        return ResponseEntity.ok(userService.finAllUsersExceptSelf(authentication));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(Authentication authentication) {
        return ResponseEntity.ok(userService.getCurrentUser(authentication));
    }

    @PutMapping("/username")
    public ResponseEntity<UserResponse> updateUsername(
            @Valid @RequestBody UserRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(userService.updateUsername(request, authentication));
    }

    @GetMapping("/check-username")
    public ResponseEntity<Map<String, Boolean>> checkUsername(@RequestParam String value) {
        return ResponseEntity.ok(Map.of("available", userService.isUsernameAvailable(value)));
    }

    @PostMapping(value = "/me/avatar", consumes = "multipart/form-data")
    public ResponseEntity<UserResponse> uploadAvatar(
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        return ResponseEntity.ok(userService.uploadAvatar(file, authentication));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<UserResponse> deleteAvatar(Authentication authentication) {
        return ResponseEntity.ok(userService.deleteAvatar(authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userService.findUserById(id));
    }

    @DeleteMapping("/me/session")
    public ResponseEntity<Void> clearSession(Authentication authentication) {
        userService.clearActiveSession(authentication);
        return ResponseEntity.noContent().build();
    }
}
