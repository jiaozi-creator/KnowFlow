package com.knowflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

public final class AppProperties {
    private AppProperties() {}

    @ConfigurationProperties("knowflow.jwt")
    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {
        public Duration accessTokenTtl() { return Duration.ofMinutes(accessTokenMinutes); }
        public Duration refreshTokenTtl() { return Duration.ofDays(refreshTokenDays); }
    }

    @ConfigurationProperties("knowflow.minio")
    public record Minio(String endpoint, String accessKey, String secretKey, String bucket) {}

    @ConfigurationProperties("knowflow.ai")
    public record Ai(String provider, String baseUrl, String apiKey, String chatModel,
                     String embeddingModel, int embeddingDimensions) {}

    @ConfigurationProperties("knowflow.cors")
    public record Cors(List<String> allowedOrigins) {}
}
