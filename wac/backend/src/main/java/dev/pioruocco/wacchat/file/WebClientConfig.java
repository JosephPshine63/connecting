package dev.pioruocco.wacchat.file;

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

    @Value("${application.file-service.base-url}")
    private String baseUrl;

    @Value("${application.file-service.internal-api-key}")
    private String internalApiKey;

    @Value("${application.file-service.connect-timeout-ms}")
    private int connectTimeoutMs;

    @Value("${application.file-service.response-timeout-ms}")
    private long responseTimeoutMs;

    @Bean
    public WebClient fileServiceWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }
}
