package dev.pioruocco.wacchat.chat;

import dev.pioruocco.wacchat.common.StringResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
@Tag(name = "Chat")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<StringResponse> createChat(
            @RequestParam(name = "receiver-id") String receiverId,
            Authentication authentication
    ) {
        final String chatId = chatService.createChat(authentication.getName(), receiverId);
        StringResponse response = StringResponse.builder()
                .response(chatId)
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ChatResponse>> getChatsByReceiver(Authentication authentication) {
        return ResponseEntity.ok(chatService.getChatsByReceiverId(authentication));
    }

    @PatchMapping("/{chatId}/accept")
    public ResponseEntity<Void> acceptChat(
            @PathVariable String chatId,
            Authentication authentication
    ) {
        chatService.acceptChat(chatId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{chatId}/reject")
    public ResponseEntity<Void> rejectChat(
            @PathVariable String chatId,
            Authentication authentication
    ) {
        chatService.rejectChat(chatId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{chatId}/favorite")
    public ResponseEntity<Map<String, Boolean>> toggleFavorite(
            @PathVariable String chatId,
            Authentication authentication
    ) {
        boolean favorite = chatService.toggleFavorite(chatId, authentication.getName());
        return ResponseEntity.ok(Map.of("favorite", favorite));
    }

    @PatchMapping("/{chatId}/archive")
    public ResponseEntity<Map<String, Boolean>> toggleArchive(
            @PathVariable String chatId,
            Authentication authentication
    ) {
        boolean archived = chatService.toggleArchive(chatId, authentication.getName());
        return ResponseEntity.ok(Map.of("archived", archived));
    }
}
