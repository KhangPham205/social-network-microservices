package com.socialnetwork.moderation_service.config;

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
 * Resource-server rules. Filing a report or a complaint is open to every authenticated user;
 * everything else in this service is an administrative view or action and is additionally guarded
 * by {@code @PreAuthorize} on the controller methods, which only works because of
 * {@link EnableMethodSecurity}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private static final String REPORTS = ApiConstants.MODERATION + "/reports";
  private static final String COMPLAINTS = ApiConstants.MODERATION + "/complaints";

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
                    // Any authenticated user may file a report or a complaint.
                    .requestMatchers(HttpMethod.POST, REPORTS)
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, COMPLAINTS)
                    .authenticated()
                    // Everything else is moderation work: admins, or holders of the matching
                    // permission checked by @PreAuthorize.
                    .requestMatchers(ApiConstants.MODERATION + "/**")
                    .authenticated()
                    .anyRequest()
                    .hasRole(SecurityConstants.ROLE_ADMIN));
    return http.build();
  }
}
