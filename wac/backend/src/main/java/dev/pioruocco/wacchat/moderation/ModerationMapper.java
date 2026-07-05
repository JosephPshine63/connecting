package dev.pioruocco.wacchat.moderation;

import dev.pioruocco.wacchat.user.User;
import dev.pioruocco.wacchat.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModerationMapper {

    private final UserRepository userRepository;

    public BlockedUserResponse toBlockedUserResponse(BlockedUser blockedUser) {
        User user = userRepository.findByPublicId(blockedUser.getBlockedId()).orElse(null);
        return BlockedUserResponse.builder()
                .id(blockedUser.getBlockedId())
                .username(user != null ? user.getUsername() : null)
                .avatarUrl(user != null ? user.getAvatarUrl() : null)
                .build();
    }
}
