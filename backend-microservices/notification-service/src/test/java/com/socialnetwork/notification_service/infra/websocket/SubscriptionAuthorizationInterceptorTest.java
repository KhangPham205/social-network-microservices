package com.socialnetwork.notification_service.infra.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.List;

/** SUBSCRIBE authorization rules of the STOMP inbound channel. */
class SubscriptionAuthorizationInterceptorTest {

  private final SubscriptionAuthorizationInterceptor interceptor =
      new SubscriptionAuthorizationInterceptor();

  private final MessageChannel channel = (message, timeout) -> true;

  private static Message<byte[]> subscribe(String destination, Principal user) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setDestination(destination);
    accessor.setUser(user);
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  private static Principal viewer() {
    return new UsernamePasswordAuthenticationToken("42", null, List.of());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/user/queue/notifications", "/user/queue/notification-summary", "/topic/public"
      })
  @DisplayName("the viewer may subscribe to their own user queue and the public topic")
  void allowsOwnQueueAndPublicTopic(String destination) {
    assertThatCode(() -> interceptor.preSend(subscribe(destination, viewer()), channel))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/queue/notifications",
        "/user/queue/",
        "/user/99/queue/notifications",
        "/topic/private",
        "/topic/conversation/5"
      })
  @DisplayName("every other destination is rejected")
  void rejectsForeignDestinations(String destination) {
    assertThatThrownBy(() -> interceptor.preSend(subscribe(destination, viewer()), channel))
        .isInstanceOf(MessageDeliveryException.class);
  }

  @Test
  @DisplayName("a SUBSCRIBE without a destination is rejected")
  void rejectsNullDestination() {
    assertThatThrownBy(() -> interceptor.preSend(subscribe(null, viewer()), channel))
        .isInstanceOf(MessageDeliveryException.class);
  }

  @Test
  @DisplayName("an unauthenticated session may not subscribe at all")
  void rejectsAnonymousSession() {
    assertThatThrownBy(
            () -> interceptor.preSend(subscribe("/user/queue/notifications", null), channel))
        .isInstanceOf(MessageDeliveryException.class)
        .hasMessageContaining("authenticated");
  }

  @Test
  @DisplayName("frames other than SUBSCRIBE pass through untouched")
  void passesThroughOtherCommands() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
    accessor.setDestination("/app/anything");
    accessor.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    assertThat(interceptor.preSend(message, channel)).isSameAs(message);
  }
}
