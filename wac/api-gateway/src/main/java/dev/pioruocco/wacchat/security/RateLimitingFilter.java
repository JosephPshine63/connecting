package dev.pioruocco.wacchat.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory per-IP token bucket — no Redis dependency. State is per gateway instance
 * and lost on restart, which is an accepted trade-off since only a single gateway
 * instance runs today; revisit with a shared store (Redis) if the gateway is ever
 * scaled horizontally.
 */
@Component
@RequiredArgsConstructor
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final RateLimitProperties properties;

    private final ConcurrentMap<String, Bucket> callBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> sensitiveBuckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        Bucket bucket = null;
        if (matchesAny(properties.getCallPaths(), path)) {
            bucket = callBuckets.computeIfAbsent(clientIp(exchange), ip -> newBucket(properties.getCallCapacity()));
        } else if (matchesAny(properties.getSensitivePaths(), path)) {
            bucket = sensitiveBuckets.computeIfAbsent(clientIp(exchange), ip -> newBucket(properties.getSensitiveCapacity()));
        }

        if (bucket == null || bucket.tryConsume(1)) {
            return chain.filter(exchange);
        }
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return exchange.getResponse().setComplete();
    }

    private Bucket newBucket(int capacity) {
        Duration window = Duration.ofSeconds(properties.getWindowSeconds());
        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(capacity, window));
        return Bucket.builder().addLimit(limit).build();
    }

    private boolean matchesAny(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
