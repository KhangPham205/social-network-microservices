package com.socialnetwork.media_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth
                    // 1. MỞ CỬA CHO SWAGGER
                    .requestMatchers(
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/aggregate/**")
                    .permitAll()

                    // 2. Tạm thời cho phép tất cả API Media đi qua để test (Sau này gắn JWT Filter
                    // vào đây)
                    .requestMatchers("/api/v1/media/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated());

    return http.build();
  }
}
