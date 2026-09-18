package com.socialnetwork.media_service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code ai-service.url}: base URL of the Python embedding service (env {@code AI_SERVICE_URL}). */
@Validated
@ConfigurationProperties(prefix = "ai-service")
public record AiServiceProperties(@NotBlank String url) {}
