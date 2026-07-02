package dev.pioruocco.wacchat.bot;

import dev.pioruocco.wacchat.user.User;
import dev.pioruocco.wacchat.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Idempotently upserts the fixed Arno bot user row at boot. Gated on the same
 * "Gemini API key present" condition as the rest of the bot feature so that leaving
 * GEMINI_API_KEY unset disables Arno entirely, rather than leaving a dead-end chat
 * with a bot that can never reply.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BotUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;

    @Value("${application.bot.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${application.bot.arno.avatar-url:}")
    private String avatarUrl;

    @Override
    public void run(ApplicationArguments args) {
        if (geminiApiKey.isBlank()) {
            log.info("GEMINI_API_KEY not configured — Arno bot user seeding skipped, feature disabled");
            return;
        }
        User bot = userRepository.findByPublicId(BotConstants.ARNO_USER_ID).orElseGet(this::newBotUser);
        bot.setFirstName(BotConstants.ARNO_FIRST_NAME);
        bot.setLastName(BotConstants.ARNO_LAST_NAME);
        bot.setAvatarUrl(avatarUrl.isBlank() ? null : avatarUrl);
        userRepository.save(bot);
        log.info("Arno bot user ready ({})", BotConstants.ARNO_USER_ID);
    }

    private User newBotUser() {
        User bot = new User();
        bot.setId(BotConstants.ARNO_USER_ID);
        bot.setFirstName(BotConstants.ARNO_FIRST_NAME);
        bot.setLastName(BotConstants.ARNO_LAST_NAME);
        bot.setEmail(BotConstants.ARNO_EMAIL);
        bot.setUsername(BotConstants.ARNO_USERNAME);
        return bot;
    }
}
