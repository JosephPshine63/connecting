package dev.pioruocco.wacchat.user;

import dev.pioruocco.wacchat.bot.BotService;
import dev.pioruocco.wacchat.chat.ChatRepository;
import dev.pioruocco.wacchat.file.FileServiceClient;
import dev.pioruocco.wacchat.moderation.ModerationService;
import dev.pioruocco.wacchat.notification.NotificationService;
import dev.pioruocco.wacchat.support.AdminChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String USER_ID = "user-1";
    private static final String OTHER_ID = "user-2";

    @Mock
    private UserRepository userRepository;
    @Mock
    private FileServiceClient fileServiceClient;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private BotService botService;
    @Mock
    private AdminChatService adminChatService;
    @Mock
    private ModerationService moderationService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, new UserMapper(), fileServiceClient, chatRepository,
                notificationService, botService, adminChatService, moderationService);
    }

    private static User userWithEmail(String id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(id + "-username");
        return user;
    }

    @Test
    void finAllUsersExceptSelf_doesNotExposeOtherUsersEmail() {
        when(moderationService.getBlockedIds(USER_ID)).thenReturn(List.of());
        when(userRepository.findAllUsersExceptSelf(USER_ID))
                .thenReturn(List.of(userWithEmail(OTHER_ID, "other@example.com")));

        List<UserResponse> result = userService.finAllUsersExceptSelf(new TestingAuthenticationToken(USER_ID, null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isNull();
        assertThat(result.get(0).getId()).isEqualTo(OTHER_ID);
    }

    @Test
    void findUserById_doesNotExposeEmail() {
        when(userRepository.findByPublicId(OTHER_ID)).thenReturn(Optional.of(userWithEmail(OTHER_ID, "other@example.com")));
        when(moderationService.isBlocked(USER_ID, OTHER_ID)).thenReturn(false);

        UserResponse result = userService.findUserById(OTHER_ID, new TestingAuthenticationToken(USER_ID, null));

        assertThat(result.getEmail()).isNull();
    }

    @Test
    void findUserById_returns404WhenTargetIsBlocked() {
        when(userRepository.findByPublicId(OTHER_ID)).thenReturn(Optional.of(userWithEmail(OTHER_ID, "other@example.com")));
        when(moderationService.isBlocked(USER_ID, OTHER_ID)).thenReturn(true);

        assertThatThrownBy(() -> userService.findUserById(OTHER_ID, new TestingAuthenticationToken(USER_ID, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getCurrentUser_stillExposesOwnEmail() {
        when(userRepository.findByPublicId(USER_ID)).thenReturn(Optional.of(userWithEmail(USER_ID, "me@example.com")));

        UserResponse result = userService.getCurrentUser(new TestingAuthenticationToken(USER_ID, null));

        assertThat(result.getEmail()).isEqualTo("me@example.com");
    }
}
