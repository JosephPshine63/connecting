package dev.pioruocco.wacchat.ws;

import dev.pioruocco.wacchat.security.KeycloakJwtAuthenticationConverter;
import dev.pioruocco.wacchat.user.SessionGuard;
import dev.pioruocco.wacchat.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final KeycloakJwtAuthenticationConverter jwtConverter;
    private final UserRepository userRepository;
    private final SessionGuard sessionGuard;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new MessageDeliveryException("Missing or invalid Authorization header in STOMP CONNECT");
            }
            String tokenValue = authHeader.substring(7);
            try {
                Jwt jwt = jwtDecoder.decode(tokenValue);
                AbstractAuthenticationToken auth = jwtConverter.convert(jwt);
                String sid = jwt.getClaimAsString("sid");
                if (sid != null && auth != null) {
                    userRepository.findByPublicId(auth.getName())
                            .filter(user -> sessionGuard.isConflicting(user, sid))
                            .ifPresent(user -> {
                                throw new MessageDeliveryException("Blocked: another session is already active for user " + user.getId());
                            });
                }
                accessor.setUser(auth);
                log.debug("WebSocket authenticated for user {}", auth != null ? auth.getName() : "unknown");
            } catch (JwtException e) {
                throw new MessageDeliveryException("JWT validation failed: " + e.getMessage());
            }
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Principal user = accessor.getUser();
            if (user == null) {
                throw new MessageDeliveryException("Unauthenticated STOMP SUBSCRIBE");
            }
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/user/")) {
                // destination format: /user/{userId}/chat
                String[] parts = destination.split("/");
                if (parts.length >= 3) {
                    String destUserId = parts[2];
                    if (!destUserId.equals(user.getName())) {
                        log.warn("Blocked subscription attempt: user {} tried to subscribe to {}", user.getName(), destination);
                        throw new MessageDeliveryException("Forbidden: cannot subscribe to " + destination);
                    }
                }
            }
        }

        return message;
    }
}
