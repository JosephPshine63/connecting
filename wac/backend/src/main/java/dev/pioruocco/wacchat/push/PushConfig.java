package dev.pioruocco.wacchat.push;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.GeneralSecurityException;
import java.security.Security;

/**
 * Builds the web-push library's PushService bean from the VAPID keypair. Always returns a
 * real (possibly key-less) instance — a @Bean method returning null isn't a reliable
 * signal for a non-Optional constructor-injected dependency across every bean-resolution
 * path (breaks WacchatApiApplicationTests' full context load), so "Web Push disabled"
 * is instead exposed via dev.pioruocco.wacchat.push.PushService#isEnabled(), which checks
 * the same VAPID @Value properties directly (mirrors BotService.isEnabled()). A key-less
 * instance is simply never used, since sendPush() bails out before calling it.
 */
@Configuration
@Slf4j
public class PushConfig {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Bean
    public nl.martijndwars.webpush.PushService webPushService(
            @Value("${application.push.vapid-public-key:}") String publicKey,
            @Value("${application.push.vapid-private-key:}") String privateKey,
            @Value("${application.push.vapid-subject:mailto:admin@wacchat.win}") String subject) {
        if (publicKey.isBlank() || privateKey.isBlank()) {
            log.info("VAPID keys not configured — Web Push is disabled");
            return new nl.martijndwars.webpush.PushService();
        }
        try {
            return new nl.martijndwars.webpush.PushService(publicKey, privateKey, subject);
        } catch (GeneralSecurityException e) {
            log.error("Failed to initialize Web Push service — invalid VAPID keys", e);
            return new nl.martijndwars.webpush.PushService();
        }
    }
}
