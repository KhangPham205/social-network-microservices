package com.socialnetwork.common.security;

import static com.socialnetwork.common.constants.SecurityConstants.ROLE_INTERNAL;

import lombok.RequiredArgsConstructor;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Applies the stateless resource-server baseline shared by every service:
 *
 * <ul>
 *   <li>CSRF and service-level CORS disabled (CORS is terminated at the API gateway)
 *   <li>stateless sessions, JSON 401 / 403 bodies
 *   <li>{@link InternalTokenAuthenticationFilter} then {@link JwtAuthenticationFilter}
 *   <li>{@code /api/v1/&#42;/internal/**} restricted to {@code ROLE_INTERNAL}
 * </ul>
 *
 * <pre>{@code
 * @Bean
 * SecurityFilterChain filterChain(HttpSecurity http, JwtSecurityConfigurer jwt) throws Exception {
 *   jwt.apply(http)
 *      .authorizeHttpRequests(a -> a.requestMatchers(ApiConstants.SWAGGER_WHITELIST).permitAll()
 *                                  .anyRequest().authenticated());
 *   return http.build();
 * }
 * }</pre>
 */
@RequiredArgsConstructor
public class JwtSecurityConfigurer {

  public static final String INTERNAL_PATTERN = "/api/v1/*/internal/**";

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final InternalTokenAuthenticationFilter internalTokenAuthenticationFilter;
  private final JsonAuthenticationEntryPoint authenticationEntryPoint;
  private final JsonAccessDeniedHandler accessDeniedHandler;

  public HttpSecurity apply(HttpSecurity http) throws Exception {
    return apply(http, AbstractHttpConfigurer::disable);
  }

  public HttpSecurity apply(HttpSecurity http, Customizer<CorsConfigurer<HttpSecurity>> cors)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(a -> a.requestMatchers(INTERNAL_PATTERN).hasRole(ROLE_INTERNAL))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(internalTokenAuthenticationFilter, JwtAuthenticationFilter.class);
    return http;
  }
}
