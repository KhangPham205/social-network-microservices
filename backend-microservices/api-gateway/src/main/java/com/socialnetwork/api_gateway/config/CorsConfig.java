package com.socialnetwork.api_gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Global CORS handling. The gateway is the only component that terminates CORS; downstream
 * services keep CORS disabled so no duplicate headers are produced.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

  private static final List<String> ALLOWED_METHODS =
      List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");
  private static final List<String> ALLOWED_HEADERS =
      List.of("Origin", "Content-Type", "Accept", "Authorization", "X-Requested-With");
  private static final long MAX_AGE_SECONDS = 3600L;

  @Bean
  public CorsWebFilter corsWebFilter(CorsProperties properties) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(properties.allowedOrigins());
    config.setAllowedMethods(ALLOWED_METHODS);
    config.setAllowedHeaders(ALLOWED_HEADERS);
    config.setAllowCredentials(true);
    config.setMaxAge(MAX_AGE_SECONDS);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsWebFilter(source);
  }
}
