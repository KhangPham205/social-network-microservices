package com.socialnetwork.notification_service.config;

import com.socialnetwork.common.security.InternalTokenInterceptor;
import com.socialnetwork.notification_service.client.UserServiceClient;
import java.time.Duration;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * The two {@link RestClient.Builder} flavours this service needs: a plain one for absolute URLs
 * (the Eureka transport resolves an unqualified builder) and a load-balanced one carrying the
 * internal token for calls to other services.
 */
@Configuration
public class RestClientConfig {

  public static final String LOAD_BALANCED_BUILDER = "loadBalancedRestClientBuilder";

  private static final String USER_SERVICE_URL = "http://user-service";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);

  /**
   * Plain builder for direct URLs. Marked primary so the discovery client does not pick up the
   * load-balanced one and then fail to reach the registry.
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

  @Bean
  public UserServiceClient userServiceClient(
      @Qualifier(LOAD_BALANCED_BUILDER) RestClient.Builder builder) {
    RestClient restClient = builder.clone().baseUrl(USER_SERVICE_URL).build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(UserServiceClient.class);
  }

  /**
   * HttpClient5 request factory. The TCP connect timeout belongs to the connection manager; the
   * response timeout belongs to the request config.
   */
  private static ClientHttpRequestFactory requestFactory(Duration connect, Duration response) {
    var connectionManager =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(
                ConnectionConfig.custom().setConnectTimeout(Timeout.of(connect)).build())
            .build();
    var requestConfig =
        RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.of(connect))
            .setResponseTimeout(Timeout.of(response))
            .build();
    var httpClient =
        HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build();
    return new HttpComponentsClientHttpRequestFactory(httpClient);
  }
}
