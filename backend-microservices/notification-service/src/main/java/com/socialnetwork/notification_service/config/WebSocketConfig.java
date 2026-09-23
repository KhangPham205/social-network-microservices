package com.socialnetwork.notification_service.config;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.constants.WebSocketConstants;
import com.socialnetwork.notification_service.infra.websocket.JwtHandshakeInterceptor;
import com.socialnetwork.notification_service.infra.websocket.SubscriptionAuthorizationInterceptor;
import com.socialnetwork.notification_service.infra.websocket.UserIdHandshakeHandler;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * The single STOMP endpoint of this service. The handshake is authenticated by {@link
 * JwtHandshakeInterceptor} and SUBSCRIBE frames are filtered by {@link
 * SubscriptionAuthorizationInterceptor}.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  /** Handshake path routed by the gateway as {@code /ws/notification/**}. */
  public static final String ENDPOINT = ApiConstants.WEBSOCKET + "/notification";

  private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
  private final SubscriptionAuthorizationInterceptor subscriptionAuthorizationInterceptor;
  private final List<String> allowedOrigins;

  /**
   * {@code @Value} must sit on the constructor parameter: Lombok does not copy it onto the
   * generated constructor, so a {@code @RequiredArgsConstructor} + field annotation would make
   * Spring look for a {@code List<String>} bean instead of reading the property.
   */
  public WebSocketConfig(
      JwtHandshakeInterceptor jwtHandshakeInterceptor,
      SubscriptionAuthorizationInterceptor subscriptionAuthorizationInterceptor,
      @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
    this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    this.subscriptionAuthorizationInterceptor = subscriptionAuthorizationInterceptor;
    this.allowedOrigins = List.copyOf(allowedOrigins);
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker(WebSocketConstants.TOPIC_PREFIX, WebSocketConstants.QUEUE_PREFIX);
    registry.setApplicationDestinationPrefixes(WebSocketConstants.APP_PREFIX);
    registry.setUserDestinationPrefix(WebSocketConstants.USER_PREFIX);
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint(ENDPOINT)
        .addInterceptors(jwtHandshakeInterceptor)
        .setHandshakeHandler(new UserIdHandshakeHandler())
        .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(subscriptionAuthorizationInterceptor);
  }
}
