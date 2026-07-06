package dev.pioruocco.wacchat.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitingFilterTest {

    private RateLimitingFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setCallPaths(List.of("/api/v1/calls/**"));
        properties.setSensitivePaths(List.of("/api/v1/users/check-username"));
        properties.setCallCapacity(2);
        properties.setSensitiveCapacity(2);
        properties.setWindowSeconds(10);
        filter = new RateLimitingFilter(properties);

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private ServerWebExchange exchangeFor(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path)
                .remoteAddress(new InetSocketAddress("10.0.0.1", 12345))
                .build();
        return MockServerWebExchange.from(request);
    }

    @Test
    void withinCapacity_allRequestsPassThrough() {
        for (int i = 0; i < 2; i++) {
            ServerWebExchange exchange = exchangeFor("/api/v1/calls/chat-1/invite");
            filter.filter(exchange, chain).block();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    @Test
    void exceedingCapacity_returnsTooManyRequests() {
        for (int i = 0; i < 2; i++) {
            filter.filter(exchangeFor("/api/v1/calls/chat-1/invite"), chain).block();
        }
        ServerWebExchange thirdExchange = exchangeFor("/api/v1/calls/chat-1/invite");

        filter.filter(thirdExchange, chain).block();

        assertThat(thirdExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void unrelatedPath_isNotRateLimited() {
        for (int i = 0; i < 10; i++) {
            ServerWebExchange exchange = exchangeFor("/api/v1/messages");
            filter.filter(exchange, chain).block();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    @Test
    void differentClientIps_haveIndependentBuckets() {
        MockServerHttpRequest requestA = MockServerHttpRequest.get("/api/v1/calls/chat-1/invite")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 1)).build();
        MockServerHttpRequest requestB = MockServerHttpRequest.get("/api/v1/calls/chat-1/invite")
                .remoteAddress(new InetSocketAddress("10.0.0.2", 1)).build();

        for (int i = 0; i < 2; i++) {
            filter.filter(MockServerWebExchange.from(requestA), chain).block();
        }
        ServerWebExchange exchangeB = MockServerWebExchange.from(requestB);

        filter.filter(exchangeB, chain).block();

        assertThat(exchangeB.getResponse().getStatusCode()).isNull();
    }
}
