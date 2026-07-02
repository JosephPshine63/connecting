package dev.pioruocco.wacchat.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * No HTTP endpoint here needs JWT enforcement (the only ones are the SockJS /ws
 * handshake and actuator) — auth happens per-STOMP-frame in AuthChannelInterceptor,
 * which uses the JwtDecoder/KeycloakJwtAuthenticationConverter beans directly rather
 * than going through this filter chain. Mirrors how backend permitAll'd /ws/** before
 * this service was extracted.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(req -> req.anyRequest().permitAll());
        return http.build();
    }
}
