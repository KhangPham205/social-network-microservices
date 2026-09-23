package com.socialnetwork.auth_service.config;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authorization rules of auth-service. The stateless resource-server baseline (CSRF off, JSON
 * 401/403, JWT and internal-token filters, {@code /api/v1/auth/internal/**} restricted to {@code
 * ROLE_INTERNAL}) comes from the shared {@link JwtSecurityConfigurer}.
 *
 * <p>Only the anonymous entry points of the authentication flow are public; everything else needs a
 * valid access token, and {@code /api/v1/auth/admin/**} additionally needs {@code ROLE_ADMIN}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  /** RBAC and staff management; requires {@code ROLE_ADMIN}. */
  public static final String ADMIN_PATH = ApiConstants.AUTH + "/admin";

  /** Endpoints a caller must reach before it can own a token. */
  private static final String[] PUBLIC_AUTH_ENDPOINTS = {
    ApiConstants.AUTH + "/register",
    ApiConstants.AUTH + "/login",
    ApiConstants.AUTH + "/logout",
    ApiConstants.AUTH + "/refresh",
    ApiConstants.AUTH + "/sendVerifyEmail",
    ApiConstants.AUTH + "/resendVerifyEmail",
    ApiConstants.AUTH + "/reset-password",
    ApiConstants.AUTH + "/verify-otp"
  };

  private final JwtSecurityConfigurer jwtSecurity;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    jwtSecurity
        .apply(http)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS)
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
