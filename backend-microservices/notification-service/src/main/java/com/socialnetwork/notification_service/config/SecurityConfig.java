package com.socialnetwork.notification_service.config;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.security.JwtSecurityConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtSecurityConfigurer jwtSecurity;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
                    // The STOMP handshake authenticates itself (JwtHandshakeInterceptor).
                    .requestMatchers(ApiConstants.WEBSOCKET + "/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated());
    return http.build();
  }
}
