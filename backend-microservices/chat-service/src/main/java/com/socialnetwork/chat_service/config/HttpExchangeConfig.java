package com.socialnetwork.chat_service.config;

import com.socialnetwork.chat_service.client.UserClient;
import com.socialnetwork.common.security.InternalTokenInterceptor;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpExchangeConfig {

  public static final String LOAD_BALANCED_BUILDER = "loadBalancedRestClientBuilder";
  private static final String USER_SERVICE_URL = "http://user-service";

  /**
   * Plain builder. The Eureka client resolves an unqualified {@code RestClient.Builder}; without
   * this primary bean it would pick the load-balanced one and fail to reach the registry itself.
   */
  @Bean
  @Primary
  public RestClient.Builder defaultRestClientBuilder() {
    return RestClient.builder();
  }

  @Bean(LOAD_BALANCED_BUILDER)
  @LoadBalanced
  public RestClient.Builder loadBalancedRestClientBuilder() {
    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(2))
            .setResponseTimeout(Timeout.ofSeconds(5))
            .build();
    CloseableHttpClient httpClient =
        HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
    return RestClient.builder()
        .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));
  }

  @Bean
  public UserClient userClient(
      @Qualifier(LOAD_BALANCED_BUILDER) RestClient.Builder builder,
      InternalTokenInterceptor internalTokenInterceptor) {
    RestClient restClient =
        builder
            .clone()
            .baseUrl(USER_SERVICE_URL)
            .requestInterceptor(internalTokenInterceptor)
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(UserClient.class);
  }
}
