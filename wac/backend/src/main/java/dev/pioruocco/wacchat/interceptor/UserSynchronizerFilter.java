package dev.pioruocco.wacchat.interceptor;

import dev.pioruocco.wacchat.user.SessionConflictException;
import dev.pioruocco.wacchat.user.UserSynchronizer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class UserSynchronizerFilter extends OncePerRequestFilter {

    private final UserSynchronizer userSynchronizer;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof AnonymousAuthenticationToken)) {
            JwtAuthenticationToken token = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication());

            try {
                userSynchronizer.synchronizeWithIdp(token.getToken(), request.getHeader("X-Tab-Id"));
            } catch (SessionConflictException e) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"SESSION_CONFLICT\"}");
                return;
            } catch (DataIntegrityViolationException e) {
                // Lost a race with a concurrent request creating the same user (first login can
                // fire more than one request before the row exists). The failed attempt aborted
                // its own transaction; retry in a brand new one, where the row now exists.
                userSynchronizer.synchronizeWithIdp(token.getToken(), request.getHeader("X-Tab-Id"));
            }
        }

        filterChain.doFilter(request, response);
    }
}
