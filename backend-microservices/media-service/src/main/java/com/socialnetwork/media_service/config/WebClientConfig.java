// package com.socialnetwork.media_service.config;
//
// import com.socialnetwork.media_service.client.UserServiceClient;
// import org.springframework.cloud.client.loadbalancer.LoadBalanced;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.web.reactive.function.client.WebClient;
// import org.springframework.web.reactive.function.client.support.WebClientAdapter;
// import org.springframework.web.service.invoker.HttpServiceProxyFactory;
//
// @Configuration
// public class WebClientConfig {
//
//  @Bean
//  @LoadBalanced
//  public WebClient.Builder webClientBuilder() {
//    return WebClient.builder();
//  }
//
//  @Bean
//  public UserServiceClient userServiceClient(WebClient.Builder builder) {
//    // Tên "user-service" phải khớp chính xác với spring.application.name bên kia
//    WebClient webClient = builder.baseUrl("http://user-service").build();
//
//    HttpServiceProxyFactory factory = HttpServiceProxyFactory
//        .builderFor(WebClientAdapter.create(webClient))
//        .build();
//
//    return factory.createClient(UserServiceClient.class);
//  }
// }
