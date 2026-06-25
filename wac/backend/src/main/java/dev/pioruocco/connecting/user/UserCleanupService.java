package dev.pioruocco.connecting.user;

import dev.pioruocco.connecting.chat.Chat;
import dev.pioruocco.connecting.chat.ChatRepository;
import dev.pioruocco.connecting.message.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCleanupService {

    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;

    @Value("${application.keycloak.admin-url}")
    private String keycloakAdminUrl;

    @Value("${application.keycloak.realm}")
    private String keycloakRealm;

    @Value("${application.keycloak.admin-username}")
    private String adminUsername;

    @Value("${application.keycloak.admin-password}")
    private String adminPassword;

    @Value("${application.cleanup.inactivity-days:21}")
    private int inactivityDays;

    @Value("${application.cleanup.protected-email:}")
    private String protectedEmail;

    private final RestClient restClient = RestClient.create();

    // Runs every Monday at 03:00 AM
    @Scheduled(cron = "0 0 3 * * MON")
    public void deleteInactiveUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(inactivityDays);
        List<User> candidates = userRepository.findInactiveUsersBefore(cutoff);

        List<User> toDelete = candidates.stream()
                .filter(u -> protectedEmail.isBlank() || !protectedEmail.equalsIgnoreCase(u.getEmail()))
                .toList();

        if (toDelete.isEmpty()) {
            log.info("Cleanup: no inactive users found (cutoff: {})", cutoff);
            return;
        }

        log.info("Cleanup: deleting {} inactive user(s) (cutoff: {})", toDelete.size(), cutoff);
        for (User user : toDelete) {
            try {
                purgeUser(user);
            } catch (Exception e) {
                log.error("Cleanup: failed to delete user {} — {}", user.getEmail(), e.getMessage());
            }
        }
    }

    private void purgeUser(User user) {
        // Delete from Keycloak first; if it fails the local DB is left untouched
        // and the next run retries. 404 means already gone — treat as success.
        deleteFromKeycloak(user.getId());

        List<Chat> userChats = chatRepository.findChatsBySenderId(user.getId());
        for (Chat chat : userChats) {
            messageRepository.deleteMessagesByChatId(chat.getId());
        }
        chatRepository.deleteAll(userChats);
        userRepository.delete(user);

        log.info("Cleanup: deleted user {} ({})", user.getEmail(), user.getId());
    }

    private void deleteFromKeycloak(String userId) {
        String token = fetchAdminToken();
        restClient.delete()
                .uri(keycloakAdminUrl + "/admin/realms/" + keycloakRealm + "/users/" + userId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        (req, res) -> log.warn("Cleanup: user {} not found in Keycloak (already deleted)", userId)
                )
                .toBodilessEntity();
    }

    private String fetchAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", adminUsername);
        form.add("password", adminPassword);

        Map<String, Object> response = restClient.post()
                .uri(keycloakAdminUrl + "/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || !response.containsKey("access_token")) {
            throw new IllegalStateException("Keycloak admin token response missing access_token");
        }
        return response.get("access_token").toString();
    }
}
