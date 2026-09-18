package com.socialnetwork.media_service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code minio.*} settings.
 *
 * @param url endpoint the service uses to talk to MinIO
 * @param publicUrl host prefix written into stored media URLs (what browsers fetch)
 */
@Validated
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
    @NotBlank String url,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    @NotBlank String bucketName,
    @NotBlank String publicUrl) {}
