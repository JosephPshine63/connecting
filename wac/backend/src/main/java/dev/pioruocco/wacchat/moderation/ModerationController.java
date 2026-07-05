package dev.pioruocco.wacchat.moderation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Moderation")
public class ModerationController {

    private final ModerationService moderationService;
    private final ModerationMapper moderationMapper;

    @PostMapping("/{id}/block")
    public ResponseEntity<Void> blockUser(@PathVariable String id, Authentication authentication) {
        moderationService.blockUser(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/block")
    public ResponseEntity<Void> unblockUser(@PathVariable String id, Authentication authentication) {
        moderationService.unblockUser(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/blocked")
    public ResponseEntity<List<BlockedUserResponse>> getBlockedUsers(Authentication authentication) {
        List<BlockedUserResponse> response = moderationService.getBlockedUsers(authentication.getName())
                .stream()
                .map(moderationMapper::toBlockedUserResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/blocked-by-me")
    public ResponseEntity<Map<String, Boolean>> isBlockedByMe(@PathVariable String id, Authentication authentication) {
        boolean blocked = moderationService.isBlockedByMe(authentication.getName(), id);
        return ResponseEntity.ok(Map.of("blocked", blocked));
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<Void> reportUser(
            @PathVariable String id,
            @Valid @RequestBody UserReportRequest request,
            Authentication authentication
    ) {
        moderationService.reportUser(authentication.getName(), id, request.getReason(), request.getDetails());
        return ResponseEntity.noContent().build();
    }
}
