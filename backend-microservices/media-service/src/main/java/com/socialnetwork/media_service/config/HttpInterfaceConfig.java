package com.socialnetwork.media_service.config;

import com.socialnetwork.media_service.client.UserServiceClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** Builds the HTTP-interface proxies on top of the load-balanced {@link RestClient.Builder}. */
@Configuration
public class HttpInterfaceConfig {

  private static final String USER_SERVICE_URL = "http://user-service";

  @Bean
  public UserServiceClient userServiceClient(
      @Qualifier(RestClientConfig.LOAD_BALANCED_BUILDER) RestClient.Builder builder) {
    RestClient restClient = builder.clone().baseUrl(USER_SERVICE_URL).build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(UserServiceClient.class);
  }
}
