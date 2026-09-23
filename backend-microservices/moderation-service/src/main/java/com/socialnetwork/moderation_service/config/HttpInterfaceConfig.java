package com.socialnetwork.moderation_service.config;

import com.socialnetwork.moderation_service.client.AiServiceClient;
import com.socialnetwork.moderation_service.client.AuthClient;
import com.socialnetwork.moderation_service.client.ChatClient;
import com.socialnetwork.moderation_service.client.MediaClient;
import com.socialnetwork.moderation_service.client.UserClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** Builds the {@code @HttpExchange} proxies used to talk to the other services. */
@Configuration
public class HttpInterfaceConfig {

  private static final String AUTH_SERVICE_URL = "http://auth-service";
  private static final String USER_SERVICE_URL = "http://user-service";
  private static final String MEDIA_SERVICE_URL = "http://media-service";
  private static final String CHAT_SERVICE_URL = "http://chat-service";

  /** The AI model needs longer than a normal service call: loading and scoring is slow. */
  private static final Duration AI_CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration AI_READ_TIMEOUT = Duration.ofSeconds(30);

  @Bean
  public AuthClient authClient(
      @Qualifier(RestClientConfig.LOAD_BALANCED_BUILDER) RestClient.Builder builder) {
    return proxy(builder, AUTH_SERVICE_URL, AuthClient.class);
  }

  @Bean
  public UserClient userClient(
      @Qualifier(RestClientConfig.LOAD_BALANCED_BUILDER) RestClient.Builder builder) {
    return proxy(builder, USER_SERVICE_URL, UserClient.class);
  }

  @Bean
  public MediaClient mediaClient(
      @Qualifier(RestClientConfig.LOAD_BALANCED_BUILDER) RestClient.Builder builder) {
    return proxy(builder, MEDIA_SERVICE_URL, MediaClient.class);
  }

  @Bean
  public ChatClient chatClient(
      @Qualifier(RestClientConfig.LOAD_BALANCED_BUILDER) RestClient.Builder builder) {
    return proxy(builder, CHAT_SERVICE_URL, ChatClient.class);
  }

  /** ai-service is reached by its absolute URL: it is not registered in Eureka. */
  @Bean
  public AiServiceClient aiServiceClient(
      RestClient.Builder builder, @Value("${ai-service.url:http://localhost:8000}") String aiServiceUrl) {
    RestClient restClient =
        builder
            .clone()
            .requestFactory(RestClientConfig.requestFactory(AI_CONNECT_TIMEOUT, AI_READ_TIMEOUT))
            .baseUrl(aiServiceUrl)
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(AiServiceClient.class);
  }

  private static <T> T proxy(RestClient.Builder builder, String baseUrl, Class<T> clientType) {
    RestClient restClient = builder.clone().baseUrl(baseUrl).build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(clientType);
  }
}
