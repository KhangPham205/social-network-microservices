package com.socialnetwork.media_service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code milvus.uri}: gRPC endpoint of the vector database. */
@Validated
@ConfigurationProperties(prefix = "milvus")
public record MilvusProperties(@NotBlank String uri) {}
