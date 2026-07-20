package dev.pioruocco.wacchat.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The VAPID public key is served here (not baked into environment.ts) because it lives in
 * backend's runtime env while environment.ts is compiled into the frontend bundle at build
 * time — keeping them in sync manually across two independently-deployed images is a
 * footgun on key rotation.
 */
@RestController
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionRepository pushSubscriptionRepository;

    @Value("${application.push.vapid-public-key:}")
    private String vapidPublicKey;

    @GetMapping("/api/v1/push/vapid-public-key")
    public ResponseEntity<Map<String, String>> getVapidPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", vapidPublicKey));
    }

    // Upsert-by-endpoint: a shared device re-subscribing under a different logged-in user
    // reassigns the existing row rather than duplicating it (unique constraint on endpoint alone).
    @PostMapping("/api/v1/users/me/push-subscriptions")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscriptionRequest request, Authentication authentication) {
        PushSubscription subscription = pushSubscriptionRepository.findByEndpoint(request.endpoint())
                .orElseGet(PushSubscription::new);
        subscription.setUserId(authentication.getName());
        subscription.setEndpoint(request.endpoint());
        subscription.setP256dh(request.p256dh());
        subscription.setAuthKey(request.auth());
        pushSubscriptionRepository.save(subscription);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/users/me/push-subscriptions")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody PushUnsubscribeRequest request, Authentication authentication) {
        pushSubscriptionRepository.findByEndpoint(request.endpoint())
                .filter(sub -> sub.getUserId().equals(authentication.getName()))
                .ifPresent(pushSubscriptionRepository::delete);
        return ResponseEntity.noContent().build();
    }

    public record PushSubscriptionRequest(@NotBlank String endpoint, @NotBlank String p256dh, @NotBlank String auth) {
    }

    public record PushUnsubscribeRequest(@NotBlank String endpoint) {
    }
}
