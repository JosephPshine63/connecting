package dev.pioruocco.wacchat.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Defense-in-depth on top of backend's own InternalAuthFilter: rejects any externally
 * routed request to /api/v1/internal/** before it's ever proxied to backend-api, so a
 * leaked BACKEND_INTERNAL_API_KEY alone isn't enough to reach those endpoints through
 * the public gateway. Legitimate callers (notification-service, call-service) reach
 * backend server-to-server via BACKEND_BASE_URL directly, not through this gateway.
 */
@Component
public class InternalPathBlockingFilter implements GlobalFilter, Ordered {

    private static final String INTERNAL_PREFIX = "/api/v1/internal/";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getURI().getPath().startsWith(INTERNAL_PREFIX)) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
