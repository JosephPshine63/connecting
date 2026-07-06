package dev.pioruocco.wacchat.support;

import dev.pioruocco.wacchat.chat.ChatService;
import dev.pioruocco.wacchat.message.MessageType;
import dev.pioruocco.wacchat.message.SystemMessageSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Auto-creates a direct chat between every new user and the real admin account
 * (a normal Keycloak-issued user, not a fixed bot id like Arno), so users can report
 * bugs or reach the admin without a separate ticket/form system. Unlike BotService,
 * there is no AI/Gemini reply logic here — the admin answers manually through the
 * ordinary chat UI.
 */
@Service
@RequiredArgsConstructor
public class AdminChatService {

    private static final String WELCOME_MESSAGE =
            "Ciao! Questa è la chat diretta con l'amministratore di WacChat. "
                    + "Scrivimi pure se trovi un bug, hai un problema o vuoi segnalarmi qualcosa 🙂";

    private final ChatService chatService;
    private final SystemMessageSender systemMessageSender;

    @Value("${application.admin.user-id:}")
    private String adminUserId;

    public boolean isEnabled() {
        return !adminUserId.isBlank();
    }

    /** Called synchronously right after username-setup completes, same trigger point as
     *  BotService.createChatWithWelcomeMessage. No-op if ADMIN_USER_ID is unset, or if the
     *  new user IS the admin (avoids the admin's own onboarding creating a self-chat). */
    public void createChatWithWelcomeMessage(String realUserId) {
        if (!isEnabled() || adminUserId.equals(realUserId)) {
            return;
        }
        String chatId = chatService.createSystemChat(realUserId, adminUserId);
        systemMessageSender.saveSystemMessage(
                chatId, adminUserId, realUserId, WELCOME_MESSAGE, MessageType.TEXT);
    }
}
