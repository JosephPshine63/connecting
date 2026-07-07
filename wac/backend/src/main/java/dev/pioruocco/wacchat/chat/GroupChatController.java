package dev.pioruocco.wacchat.chat;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
@Tag(name = "Group Chat")
public class GroupChatController {

    private final GroupChatService groupChatService;

    @PostMapping("/groups")
    public ResponseEntity<ChatResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            Authentication authentication
    ) {
        ChatResponse response = groupChatService.createGroup(
                authentication.getName(), request.getMemberIds(), request.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{chatId}/members")
    public ResponseEntity<Void> addMember(
            @PathVariable String chatId,
            @Valid @RequestBody AddMemberRequest request,
            Authentication authentication
    ) {
        groupChatService.addMember(chatId, authentication.getName(), request.getUserId());
        return ResponseEntity.noContent().build();
    }

    /** Also how a member leaves the group: pass their own id as {userId}. */
    @DeleteMapping("/{chatId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable String chatId,
            @PathVariable String userId,
            Authentication authentication
    ) {
        groupChatService.removeMember(chatId, authentication.getName(), userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{chatId}/name")
    public ResponseEntity<Void> renameGroup(
            @PathVariable String chatId,
            @Valid @RequestBody RenameGroupRequest request,
            Authentication authentication
    ) {
        groupChatService.renameGroup(chatId, authentication.getName(), request.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{chatId}/members")
    public ResponseEntity<List<GroupMemberResponse>> listMembers(
            @PathVariable String chatId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(groupChatService.listMembers(chatId, authentication.getName()));
    }
}
