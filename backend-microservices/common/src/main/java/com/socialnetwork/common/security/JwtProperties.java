package com.socialnetwork.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code jwt.*} properties. {@code jwt.secret} is mandatory and must be at least 32 bytes (HS256);
 * it must come from the environment ({@code JWT_SECRET}), never from a committed file.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

  /** HMAC-SHA256 signing key shared by auth-service (issuer) and every resource server. */
  @NotBlank
  @Size(min = 32, message = "jwt.secret must be at least 32 characters (256 bits)")
  private String secret;

  /** Access-token lifetime in milliseconds (default 1 day). */
  private long expiration = 86_400_000L;

  private Refresh refresh = new Refresh();

  @Getter
  @Setter
  public static class Refresh {
    /** Refresh-token lifetime in milliseconds (default 7 days). */
    private long expiration = 604_800_000L;
  }
}
