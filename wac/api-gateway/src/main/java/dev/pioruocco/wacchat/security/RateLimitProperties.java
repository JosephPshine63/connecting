package dev.pioruocco.wacchat.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "application.rate-limit")
@Data
public class RateLimitProperties {

    private List<String> callPaths = List.of();
    private List<String> sensitivePaths = List.of();
    private int callCapacity = 30;
    private int sensitiveCapacity = 10;
    private long windowSeconds = 10;
}
