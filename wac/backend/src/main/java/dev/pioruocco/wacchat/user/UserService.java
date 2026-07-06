package dev.pioruocco.wacchat.user;

import dev.pioruocco.wacchat.bot.BotService;
import dev.pioruocco.wacchat.chat.Chat;
import dev.pioruocco.wacchat.chat.ChatRepository;
import dev.pioruocco.wacchat.file.FileServiceClient;
import dev.pioruocco.wacchat.moderation.ModerationService;
import dev.pioruocco.wacchat.notification.Notification;
import dev.pioruocco.wacchat.notification.NotificationService;
import dev.pioruocco.wacchat.notification.NotificationType;
import dev.pioruocco.wacchat.support.AdminChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final FileServiceClient fileServiceClient;
    private final ChatRepository chatRepository;
    private final NotificationService notificationService;
    private final BotService botService;
    private final AdminChatService adminChatService;
    private final ModerationService moderationService;

    public List<UserResponse> finAllUsersExceptSelf(Authentication connectedUser) {
        Set<String> blockedIds = Set.copyOf(moderationService.getBlockedIds(connectedUser.getName()));
        return userRepository.findAllUsersExceptSelf(connectedUser.getName())
                .stream()
                .filter(user -> !blockedIds.contains(user.getId()))
                .map(userMapper::toPublicUserResponse)
                .toList();
    }

    public UserResponse getCurrentUser(Authentication authentication) {
        return userRepository.findByPublicId(authentication.getName())
                .map(userMapper::toUserResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public UserResponse updateUsername(UserRequest request, Authentication authentication) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        User user = userRepository.findByPublicId(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setUsername(request.getUsername());
        UserResponse response = userMapper.toUserResponse(userRepository.save(user));
        try {
            botService.createChatWithWelcomeMessage(user.getId());
        } catch (Exception e) {
            log.error("Failed to create Arno welcome chat for user {}: {}", user.getId(), e.getMessage());
        }
        try {
            adminChatService.createChatWithWelcomeMessage(user.getId());
        } catch (Exception e) {
            log.error("Failed to create admin chat for user {}: {}", user.getId(), e.getMessage());
        }
        return response;
    }

    public boolean isUsernameAvailable(String value) {
        return !userRepository.existsByUsername(value);
    }

    public UserResponse uploadAvatar(MultipartFile file, Authentication authentication) {
        User user = userRepository.findByPublicId(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String bearerToken = bearerToken(authentication);
        String oldAvatarUrl = user.getAvatarUrl();
        String newAvatarUrl = fileServiceClient.uploadAvatar(file, user.getId(), bearerToken);
        user.setAvatarUrl(newAvatarUrl);
        UserResponse response = userMapper.toUserResponse(userRepository.save(user));
        if (oldAvatarUrl != null) {
            fileServiceClient.deleteAvatar(oldAvatarUrl, bearerToken);
        }
        notifyChatPartnersOfAvatarChange(user.getId(), newAvatarUrl);
        return response;
    }

    public UserResponse deleteAvatar(Authentication authentication) {
        User user = userRepository.findByPublicId(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String oldAvatarUrl = user.getAvatarUrl();
        user.setAvatarUrl(null);
        UserResponse response = userMapper.toUserResponse(userRepository.save(user));
        if (oldAvatarUrl != null) {
            fileServiceClient.deleteAvatar(oldAvatarUrl, bearerToken(authentication));
        }
        notifyChatPartnersOfAvatarChange(user.getId(), null);
        return response;
    }

    private static String bearerToken(Authentication authentication) {
        return ((Jwt) authentication.getPrincipal()).getTokenValue();
    }

    public UserResponse findUserById(String id) {
        return userRepository.findByPublicId(id)
                .map(userMapper::toPublicUserResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public void clearActiveSession(Authentication authentication, String tabId) {
        userRepository.findByPublicId(authentication.getName())
                .ifPresent(user -> {
                    // Only release the lock if it's still held by the tab that's asking — a
                    // stale/losing tab closing shouldn't be able to kick out the tab that has
                    // since taken over the single-session lock.
                    if (tabId == null || tabId.equals(user.getActiveSessionId())) {
                        user.setActiveSessionId(null);
                        userRepository.save(user);
                    }
                });
    }

    private void notifyChatPartnersOfAvatarChange(String userId, String newAvatarUrl) {
        Set<String> partnerIds = chatRepository.findChatsBySenderId(userId).stream()
                .map(chat -> otherUserId(chat, userId))
                .collect(Collectors.toSet());
        Notification notification = Notification.builder()
                .type(NotificationType.AVATAR_UPDATED)
                .senderId(userId)
                .avatarUrl(newAvatarUrl)
                .build();
        partnerIds.forEach(partnerId -> notificationService.sendNotification(partnerId, notification));
    }

    private String otherUserId(Chat chat, String userId) {
        return chat.getSender().getId().equals(userId) ? chat.getRecipient().getId() : chat.getSender().getId();
    }
}
