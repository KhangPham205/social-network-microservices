package com.socialnetwork.notification_service.config;

import com.socialnetwork.notification_service.client.UserServiceClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpInterfaceConfig {

  @Bean
  public UserServiceClient userServiceClient(
      @Qualifier("microserviceBuilder") RestClient.Builder builder) {
    RestClient restClient = builder.baseUrl("http://user-service").build();
    RestClientAdapter adapter = RestClientAdapter.create(restClient);
    HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

    return factory.createClient(UserServiceClient.class);
  }
}
