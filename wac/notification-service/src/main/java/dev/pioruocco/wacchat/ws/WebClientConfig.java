package dev.pioruocco.wacchat.ws;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${application.backend.base-url}")
    private String baseUrl;

    @Value("${application.backend.internal-api-key}")
    private String internalApiKey;

    @Value("${application.backend.connect-timeout-ms}")
    private int connectTimeoutMs;

    @Value("${application.backend.response-timeout-ms}")
    private long responseTimeoutMs;

    @Bean
    public WebClient backendWebClient(WebClient.Builder webClientBuilder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs));

        return webClientBuilder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }
}
