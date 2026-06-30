package dev.pioruocco.wacchat.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSynchronizer {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final MailService mailService;
    private final SessionGuard sessionGuard;

    public void synchronizeWithIdp(Jwt token) {
        log.info("Synchronizing user with idp");
        getUserEmail(token).ifPresent(userEmail -> {
            log.info("Synchronizing user having email {}", userEmail);
            Optional<User> optUser = userRepository.findByEmail(userEmail);
            boolean isNew = optUser.isEmpty();
            User user = optUser.orElseGet(User::new);

            String sid = token.getClaimAsString("sid");
            if (sid != null) {
                if (sessionGuard.isConflicting(user, sid)) {
                    throw new SessionConflictException(user.getId());
                }
                user.setActiveSessionId(sid);
            }

            userMapper.updateFromTokenAttributes(user, token.getClaims());
            userRepository.save(user);
            if (isNew) {
                mailService.sendWelcome(user);
            }
        });
    }

    private Optional<String> getUserEmail(Jwt token) {
        Map<String, Object> attributes = token.getClaims();
        if (attributes.containsKey("email")) {
            return Optional.of(attributes.get("email").toString());
        }
        return Optional.empty();

    }
}
