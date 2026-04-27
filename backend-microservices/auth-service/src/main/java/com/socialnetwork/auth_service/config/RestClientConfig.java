package com.socialnetwork.auth_service.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

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
}
