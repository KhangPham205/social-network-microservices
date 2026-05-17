package com.socialnetwork.moderation_service.config;

import com.socialnetwork.moderation_service.client.ChatClient;
import com.socialnetwork.moderation_service.client.PostClient;
import com.socialnetwork.moderation_service.client.UserClient;
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

  @Bean
  @Primary
  public RestClient.Builder defaultRestClientBuilder() {
    return RestClient.builder();
  }

  @Bean(name = "microserviceBuilder")
  @LoadBalanced
  public RestClient.Builder loadBalancedRestClientBuilder() {

    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(2)) // timeout connect
            .setResponseTimeout(Timeout.ofSeconds(5)) // timeout read response
            .build();

    CloseableHttpClient httpClient =
        HttpClients.custom().setDefaultRequestConfig(requestConfig).build();

    HttpComponentsClientHttpRequestFactory factory =
        new HttpComponentsClientHttpRequestFactory(httpClient);

    return RestClient.builder().requestFactory(factory);
  }

  @Bean
  public PostClient postClient(@Qualifier("microserviceBuilder") RestClient.Builder builder) {
    RestClient restClient = builder.baseUrl("http://post-service").build();
    RestClientAdapter adapter = RestClientAdapter.create(restClient);
    return HttpServiceProxyFactory.builderFor(adapter).build().createClient(PostClient.class);
  }

  @Bean
  public ChatClient chatClient(@Qualifier("microserviceBuilder") RestClient.Builder builder) {
    RestClient restClient = builder.baseUrl("http://chat-service").build();
    RestClientAdapter adapter = RestClientAdapter.create(restClient);
    return HttpServiceProxyFactory.builderFor(adapter).build().createClient(ChatClient.class);
  }

  @Bean
  public UserClient userClient(@Qualifier("microserviceBuilder") RestClient.Builder builder) {
    RestClient restClient = builder.baseUrl("http://user-service").build();
    RestClientAdapter adapter = RestClientAdapter.create(restClient);
    HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

    return factory.createClient(UserClient.class);
  }
}
