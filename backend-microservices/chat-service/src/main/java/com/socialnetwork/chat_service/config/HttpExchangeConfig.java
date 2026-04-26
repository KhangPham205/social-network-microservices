package com.socialnetwork.chat_service.config;

import com.socialnetwork.chat_service.client.UserClient;
import org.springframework.beans.factory.annotation.Qualifier; // Thêm import này
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
    return RestClient.builder();
  }

  @Bean
  public UserClient userClient(@Qualifier("microserviceBuilder") RestClient.Builder builder) {
    RestClient restClient = builder.baseUrl("http://user-service").build();
    RestClientAdapter adapter = RestClientAdapter.create(restClient);
    HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

    return factory.createClient(UserClient.class);
  }
}