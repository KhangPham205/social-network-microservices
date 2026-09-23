package com.socialnetwork.auth_service.config;

import com.socialnetwork.common.security.InternalTokenInterceptor;
import java.time.Duration;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

  public static final String LOAD_BALANCED_BUILDER = "loadBalancedRestClientBuilder";

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);

  /**
   * Plain builder for direct URLs. Marked primary so the discovery client does not pick up the
   * load-balanced one.
   */
  @Bean
  @Primary
  public RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }

  /**
   * Builder for service-to-service calls resolved through Eureka ({@code http://user-service}).
   * Carries the internal token so {@code /internal/**} endpoints accept the call.
   */
  @Bean(LOAD_BALANCED_BUILDER)
  @LoadBalanced
  public RestClient.Builder loadBalancedRestClientBuilder(InternalTokenInterceptor interceptor) {
    return RestClient.builder()
        .requestFactory(requestFactory(CONNECT_TIMEOUT, RESPONSE_TIMEOUT))
        .requestInterceptor(interceptor);
  }

  /** A slow or unreachable peer must fail fast instead of blocking a request thread. */
  private static ClientHttpRequestFactory requestFactory(Duration connect, Duration response) {
    ConnectionConfig connectionConfig =
        ConnectionConfig.custom().setConnectTimeout(Timeout.of(connect)).build();
    PoolingHttpClientConnectionManager connectionManager =
        new PoolingHttpClientConnectionManager();
    connectionManager.setDefaultConnectionConfig(connectionConfig);

    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.of(connect))
            .setResponseTimeout(Timeout.of(response))
            .build();

    return new HttpComponentsClientHttpRequestFactory(
        HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build());
  }
}
