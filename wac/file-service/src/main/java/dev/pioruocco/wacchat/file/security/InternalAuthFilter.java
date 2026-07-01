package dev.pioruocco.wacchat.file.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    @Value("${application.internal-api-key}")
    private String internalApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String providedKey = request.getHeader(INTERNAL_API_KEY_HEADER);
        if (internalApiKey == null || internalApiKey.isBlank() || !internalApiKey.equals(providedKey)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid internal API key");
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }
}
