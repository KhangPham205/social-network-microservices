package com.socialnetwork.notification_service.infra.websocket;

import com.socialnetwork.common.constants.WebSocketConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Restricts SUBSCRIBE frames to the caller's own user queue ({@code /user/queue/**}, which the
 * broker binds to the session principal) and the public presence topic. Everything else, in
 * particular raw {@code /queue/...} destinations of other sessions, is rejected with a STOMP ERROR.
 */
@Slf4j
@Component
public class SubscriptionAuthorizationInterceptor implements ChannelInterceptor {

  static final String USER_QUEUE_PREFIX =
      WebSocketConstants.USER_PREFIX + WebSocketConstants.QUEUE_PREFIX + "/";

  @Override
  public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
    StompHeaderAccessor accessor =
        StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
      return message;
    }

    if (accessor.getUser() == null) {
      throw new MessageDeliveryException(message, "Subscription requires an authenticated session");
    }

    String destination = accessor.getDestination();
    if (!isAllowed(destination)) {
      log.warn(
          "User {} attempted to subscribe to forbidden destination {}",
          accessor.getUser().getName(),
          destination);
      throw new MessageDeliveryException(message, "Subscription to this destination is not allowed");
    }
    return message;
  }

  static boolean isAllowed(String destination) {
    if (destination == null) {
      return false;
    }
    return WebSocketConstants.PUBLIC_TOPIC.equals(destination)
        || (destination.startsWith(USER_QUEUE_PREFIX)
            && destination.length() > USER_QUEUE_PREFIX.length());
  }
}
