package dev.pioruocco.wacchat.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * {@code tabId} identifies the browser tab (not the Keycloak SSO session — two tabs of the
     * same browser share one {@code sid}), so it's what actually enforces "one active tab".
     */
    @Transactional
    public void synchronizeWithIdp(Jwt token, String tabId) {
        log.info("Synchronizing user with idp");
        getUserEmail(token).ifPresent(userEmail -> synchronizeUser(userEmail, token, tabId));
    }

    private void synchronizeUser(String userEmail, Jwt token, String tabId) {
        log.info("Synchronizing user having email {}", userEmail);
        Optional<User> optUser = userRepository.findByEmailForUpdate(userEmail);
        boolean isNew = optUser.isEmpty();
        User user = optUser.orElseGet(User::new);
        applySessionAndClaims(user, token, tabId);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost the race on the very first insert for this email — another concurrent
            // request already created the row; fall back to updating it instead.
            user = userRepository.findByEmailForUpdate(userEmail).orElseThrow(() -> e);
            isNew = false;
            applySessionAndClaims(user, token, tabId);
            userRepository.saveAndFlush(user);
        }

        if (isNew) {
            mailService.sendWelcome(user);
        }
    }

    private void applySessionAndClaims(User user, Jwt token, String tabId) {
        if (tabId != null) {
            if (sessionGuard.isConflicting(user, tabId)) {
                throw new SessionConflictException(user.getId());
            }
            user.setActiveSessionId(tabId);
        }
        userMapper.updateFromTokenAttributes(user, token.getClaims());
    }

    private Optional<String> getUserEmail(Jwt token) {
        Map<String, Object> attributes = token.getClaims();
        if (attributes.containsKey("email")) {
            return Optional.of(attributes.get("email").toString());
        }
        return Optional.empty();

    }
}
