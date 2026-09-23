package com.socialnetwork.chat_service.infra.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.socialnetwork.chat_service.service.RoomMembership;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionAuthorizationInterceptorTest {

  private static final Long ROOM_ID = 5L;
  private static final Long MEMBER_ID = 7L;
  private static final Long OUTSIDER_ID = 99L;

  @Mock private RoomMembership roomMembership;
  @Mock private MessageChannel channel;

  @InjectMocks private SubscriptionAuthorizationInterceptor interceptor;

  @Test
  void aMemberMaySubscribeToTheRoomQueueAndTopic() {
    when(roomMembership.isMember(ROOM_ID, MEMBER_ID)).thenReturn(true);

    assertThatCode(
            () ->
                interceptor.preSend(
                    subscribe("/queue/conversation/5", MEMBER_ID), channel))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                interceptor.preSend(
                    subscribe("/topic/conversation/5", MEMBER_ID), channel))
        .doesNotThrowAnyException();
  }

  @Test
  void aNonMemberIsRejected() {
    when(roomMembership.isMember(ROOM_ID, OUTSIDER_ID)).thenReturn(false);

    assertThatThrownBy(
            () -> interceptor.preSend(subscribe("/queue/conversation/5", OUTSIDER_ID), channel))
        .isInstanceOf(MessageDeliveryException.class);
  }

  @Test
  void anAnonymousSessionIsRejected() {
    assertThatThrownBy(
            () -> interceptor.preSend(subscribe("/queue/conversation/5", null), channel))
        .isInstanceOf(MessageDeliveryException.class);
  }

  @Test
  void unrelatedDestinationsAreRejected() {
    assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/public", MEMBER_ID), channel))
        .isInstanceOf(MessageDeliveryException.class);
    assertThatThrownBy(
            () -> interceptor.preSend(subscribe("/queue/conversation/abc", MEMBER_ID), channel))
        .isInstanceOf(MessageDeliveryException.class);
  }

  @Test
  void framesOtherThanSubscribePassThrough() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
    accessor.setDestination("/app/chat.send");
    accessor.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    assertThat(interceptor.preSend(message, channel)).isSameAs(message);
  }

  @Test
  void roomIdIsParsedOnlyFromConversationDestinations() {
    assertThat(SubscriptionAuthorizationInterceptor.roomIdOf("/queue/conversation/12"))
        .isEqualTo(12L);
    assertThat(SubscriptionAuthorizationInterceptor.roomIdOf("/topic/conversation/12"))
        .isEqualTo(12L);
    assertThat(SubscriptionAuthorizationInterceptor.roomIdOf("/topic/conversation/")).isNull();
    assertThat(SubscriptionAuthorizationInterceptor.roomIdOf("/queue/notifications")).isNull();
    assertThat(SubscriptionAuthorizationInterceptor.roomIdOf(null)).isNull();
  }

  private static Message<byte[]> subscribe(String destination, Long userId) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setDestination(destination);
    accessor.setSessionId("session-1");
    if (userId != null) {
      Principal principal =
          new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, List.of());
      accessor.setUser(principal);
    }
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }
}
