package dev.pioruocco.wacchat.bot;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class GeminiWebClientConfig {

    @Value("${application.bot.gemini.base-url}")
    private String baseUrl;

    @Value("${application.bot.gemini.api-key:}")
    private String apiKey;

    @Value("${application.bot.gemini.connect-timeout-ms}")
    private int connectTimeoutMs;

    @Value("${application.bot.gemini.response-timeout-ms}")
    private long responseTimeoutMs;

    @Bean
    public WebClient geminiWebClient(WebClient.Builder webClientBuilder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs));

        return webClientBuilder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }
}
