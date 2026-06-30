package dev.pioruocco.wacchat.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class R2Config {

    @Value("${application.r2.account-id}")
    private String accountId;

    @Value("${application.r2.access-key-id}")
    private String accessKeyId;

    @Value("${application.r2.secret-access-key}")
    private String secretAccessKey;

    @Bean
    public S3Client r2Client() {
        String resolvedAccountId = accountId == null || accountId.isBlank() ? "unconfigured" : accountId;
        String resolvedAccessKeyId = accessKeyId == null || accessKeyId.isBlank() ? "unconfigured" : accessKeyId;
        String resolvedSecretAccessKey = secretAccessKey == null || secretAccessKey.isBlank() ? "unconfigured" : secretAccessKey;
        return S3Client.builder()
                .endpointOverride(URI.create("https://" + resolvedAccountId + ".r2.cloudflarestorage.com"))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(resolvedAccessKeyId, resolvedSecretAccessKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
