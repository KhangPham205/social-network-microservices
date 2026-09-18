package com.socialnetwork.media_service.config;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.constants.SecurityConstants;
import com.socialnetwork.common.security.JwtSecurityConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server rules: everything under {@code /api/v1/media} needs a valid JWT, admin endpoints
 * need {@code ROLE_ADMIN}, and {@code /api/v1/media/internal/**} is only reachable with the shared
 * internal token (applied by {@link JwtSecurityConfigurer}).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  public static final String ADMIN_PATH = ApiConstants.MEDIA + "/admin";

  private final JwtSecurityConfigurer jwtSecurity;

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    jwtSecurity
        .apply(http)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(ApiConstants.SWAGGER_WHITELIST)
                    .permitAll()
                    .requestMatchers("/error", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(ADMIN_PATH + "/**")
                    .hasRole(SecurityConstants.ROLE_ADMIN)
                    .anyRequest()
                    .authenticated());
    return http.build();
  }
}
