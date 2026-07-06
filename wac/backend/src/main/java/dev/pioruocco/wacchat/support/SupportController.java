package dev.pioruocco.wacchat.support;

import dev.pioruocco.wacchat.chat.ChatResponse;
import dev.pioruocco.wacchat.chat.ChatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
@Tag(name = "Support")
public class SupportController {

    private final AdminChatService adminChatService;
    private final ChatService chatService;

    /** Backs the frontend's "Segnala un bug" button. Returns 404 if the admin-chat
     *  feature isn't configured (ADMIN_USER_ID unset) or the caller IS the admin
     *  account itself. */
    @PostMapping("/report-bug-chat")
    public ResponseEntity<ChatResponse> getOrCreateReportBugChat(Authentication authentication) {
        String chatId = adminChatService.getOrCreateChatId(authentication.getName());
        if (chatId == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(chatService.getChatResponseById(chatId, authentication.getName()));
    }
}
