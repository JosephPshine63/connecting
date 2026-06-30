package dev.pioruocco.wacchat.user;

import dev.pioruocco.wacchat.chat.Chat;
import dev.pioruocco.wacchat.chat.ChatRepository;
import dev.pioruocco.wacchat.file.R2StorageService;
import dev.pioruocco.wacchat.notification.Notification;
import dev.pioruocco.wacchat.notification.NotificationService;
import dev.pioruocco.wacchat.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final R2StorageService r2StorageService;
    private final ChatRepository chatRepository;
    private final NotificationService notificationService;

    public List<UserResponse> finAllUsersExceptSelf(Authentication connectedUser) {
        return userRepository.findAllUsersExceptSelf(connectedUser.getName())
                .stream()
                .map(userMapper::toUserResponse)
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
        return userMapper.toUserResponse(userRepository.save(user));
    }

    public boolean isUsernameAvailable(String value) {
        return !userRepository.existsByUsername(value);
    }

    public UserResponse uploadAvatar(MultipartFile file, Authentication authentication) {
        User user = userRepository.findByPublicId(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String oldAvatarUrl = user.getAvatarUrl();
        String newAvatarUrl = r2StorageService.uploadAvatar(file, user.getId());
        user.setAvatarUrl(newAvatarUrl);
        UserResponse response = userMapper.toUserResponse(userRepository.save(user));
        if (oldAvatarUrl != null) {
            r2StorageService.deleteAvatar(oldAvatarUrl);
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
            r2StorageService.deleteAvatar(oldAvatarUrl);
        }
        notifyChatPartnersOfAvatarChange(user.getId(), null);
        return response;
    }

    public UserResponse findUserById(String id) {
        return userRepository.findByPublicId(id)
                .map(userMapper::toUserResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public void clearActiveSession(Authentication authentication) {
        userRepository.findByPublicId(authentication.getName())
                .ifPresent(user -> {
                    user.setActiveSessionId(null);
                    userRepository.save(user);
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
