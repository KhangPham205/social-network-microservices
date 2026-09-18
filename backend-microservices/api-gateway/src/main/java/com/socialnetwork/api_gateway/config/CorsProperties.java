package com.socialnetwork.api_gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS settings of the gateway, bound from {@code app.cors.*}.
 *
 * @param allowedOrigins exact origins allowed to call the API with credentials (bound from
 *     {@code CORS_ALLOWED_ORIGINS}, comma separated)
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

  public CorsProperties {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }
}
