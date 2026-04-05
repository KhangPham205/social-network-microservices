package com.socialnetwork.notification_service.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

  // 1. Client mặc định (Dành cho Eureka và các request ra bên ngoài)
  @Bean
  @Primary
  public RestClient.Builder defaultRestClientBuilder() {
    return RestClient.builder();
  }

  // 2. Client có Load Balancer (Dành riêng cho giao tiếp Microservices)
  @Bean(name = "microserviceBuilder")
  @LoadBalanced
  public RestClient.Builder loadBalancedRestClientBuilder() {
    return RestClient.builder();
  }
}
