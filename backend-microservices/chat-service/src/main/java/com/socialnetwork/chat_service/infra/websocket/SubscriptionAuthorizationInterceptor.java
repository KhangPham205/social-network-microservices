package com.socialnetwork.chat_service.infra.websocket;

import com.socialnetwork.chat_service.service.RoomMembership;
import com.socialnetwork.common.constants.WebSocketConstants;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
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
 * Rejects SUBSCRIBE frames for {@code /queue/conversation/{roomId}} and {@code
 * /topic/conversation/{roomId}} unless the session principal is a member of that room. Any other
 * destination is refused outright, so a client cannot listen in on someone else's conversation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionAuthorizationInterceptor implements ChannelInterceptor {

  static final String QUEUE_PREFIX = WebSocketConstants.CONVERSATION_QUEUE + "/";
  static final String TOPIC_PREFIX = WebSocketConstants.CONVERSATION_TOPIC + "/";

  private final RoomMembership roomMembership;

  @Override
  public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
    StompHeaderAccessor accessor =
        StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
      return message;
    }

    Principal user = accessor.getUser();
    if (user == null) {
      throw new MessageDeliveryException(message, "Subscription requires an authenticated session");
    }

    Long roomId = roomIdOf(accessor.getDestination());
    if (roomId == null) {
      log.warn("Rejected subscription to unsupported destination {}", accessor.getDestination());
      throw new MessageDeliveryException(
          message, "Subscription to this destination is not allowed");
    }

    Long userId = userIdOf(user);
    if (userId == null || !roomMembership.isMember(roomId, userId)) {
      log.warn("User {} is not a member of conversation {}", user.getName(), roomId);
      throw new MessageDeliveryException(message, "You are not a member of this conversation");
    }
    return message;
  }

  /** @return the room id of a conversation destination, or {@code null} for anything else */
  static Long roomIdOf(String destination) {
    if (destination == null) {
      return null;
    }
    String suffix;
    if (destination.startsWith(QUEUE_PREFIX)) {
      suffix = destination.substring(QUEUE_PREFIX.length());
    } else if (destination.startsWith(TOPIC_PREFIX)) {
      suffix = destination.substring(TOPIC_PREFIX.length());
    } else {
      return null;
    }
    try {
      return Long.valueOf(suffix);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Long userIdOf(Principal user) {
    try {
      return Long.valueOf(user.getName());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
