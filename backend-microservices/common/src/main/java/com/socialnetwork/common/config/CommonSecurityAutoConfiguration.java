package com.socialnetwork.common.config;

import com.socialnetwork.common.security.BearerTokenRelayInterceptor;
import com.socialnetwork.common.security.InternalApiProperties;
import com.socialnetwork.common.security.InternalTokenAuthenticationFilter;
import com.socialnetwork.common.security.InternalTokenInterceptor;
import com.socialnetwork.common.security.JsonAccessDeniedHandler;
import com.socialnetwork.common.security.JsonAuthenticationEntryPoint;
import com.socialnetwork.common.security.JwtAuthenticationFilter;
import com.socialnetwork.common.security.JwtProperties;
import com.socialnetwork.common.security.JwtSecurityConfigurer;
import com.socialnetwork.common.security.JwtValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers the JWT resource-server building blocks in any servlet service that has Spring Security
 * on the classpath. Nothing here creates a {@code SecurityFilterChain}: each service owns its
 * authorization rules (see {@link JwtSecurityConfigurer}).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(HttpSecurity.class)
@EnableConfigurationProperties({JwtProperties.class, InternalApiProperties.class})
public class CommonSecurityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public JwtValidator jwtValidator(JwtProperties properties) {
    return new JwtValidator(properties.getSecret());
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtAuthenticationFilter jwtAuthenticationFilter(JwtValidator jwtValidator) {
    return new JwtAuthenticationFilter(jwtValidator);
  }

  /**
   * The filter is added to the Spring Security chain explicitly; stop Boot from also registering
   * it as a plain servlet filter for every request.
   */
  @Bean
  public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
      JwtAuthenticationFilter filter) {
    FilterRegistrationBean<JwtAuthenticationFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  @ConditionalOnMissingBean
  public JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
    return new JsonAuthenticationEntryPoint(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public JsonAccessDeniedHandler jsonAccessDeniedHandler(ObjectMapper objectMapper) {
    return new JsonAccessDeniedHandler(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public InternalTokenAuthenticationFilter internalTokenAuthenticationFilter(
      InternalApiProperties properties) {
    return new InternalTokenAuthenticationFilter(properties.getToken());
  }

  @Bean
  public FilterRegistrationBean<InternalTokenAuthenticationFilter>
      internalTokenAuthenticationFilterRegistration(InternalTokenAuthenticationFilter filter) {
    FilterRegistrationBean<InternalTokenAuthenticationFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  /** Add to RestClient builders that call other services' /internal/** endpoints. */
  @Bean
  @ConditionalOnMissingBean
  public InternalTokenInterceptor internalTokenInterceptor(InternalApiProperties properties) {
    return new InternalTokenInterceptor(properties.getToken());
  }

  /** Add to RestClient builders that must act on behalf of the current user. */
  @Bean
  @ConditionalOnMissingBean
  public BearerTokenRelayInterceptor bearerTokenRelayInterceptor() {
    return new BearerTokenRelayInterceptor();
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtSecurityConfigurer jwtSecurityConfigurer(
      JwtAuthenticationFilter jwtFilter,
      InternalTokenAuthenticationFilter internalFilter,
      JsonAuthenticationEntryPoint entryPoint,
      JsonAccessDeniedHandler deniedHandler) {
    return new JwtSecurityConfigurer(jwtFilter, internalFilter, entryPoint, deniedHandler);
  }
}
