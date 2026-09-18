package com.socialnetwork.common.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.internal.token}: shared secret that services send in {@code X-Internal-Token} when
 * calling each other's {@code /internal/**} endpoints. Comes from env {@code INTERNAL_API_TOKEN}.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.internal")
public class InternalApiProperties {

  @NotBlank private String token;
}
