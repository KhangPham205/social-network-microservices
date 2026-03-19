package com.socialnetwork.auth_service.config;

import com.socialnetwork.auth_service.client.UserServiceClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpInterfaceConfig {

  // Nhờ Spring tiêm đúng cái "microserviceBuilder" từ bên RestClientConfig sang đây
  @Bean
  public UserServiceClient userServiceClient(@Qualifier("microserviceBuilder") RestClient.Builder builder) {
    RestClient restClient = builder.baseUrl("http://user-service").build();
    RestClientAdapter adapter = RestClientAdapter.create(restClient);
    HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

    return factory.createClient(UserServiceClient.class);
  }
}