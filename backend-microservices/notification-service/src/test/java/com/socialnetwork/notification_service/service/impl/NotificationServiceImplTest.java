package com.socialnetwork.notification_service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.socialnetwork.common.events.MessageNotificationEvent;
import com.socialnetwork.common.events.NotificationEvent;
import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.vo.NotificationType;
import com.socialnetwork.notification_service.dto.NotificationDto;
import com.socialnetwork.notification_service.infra.websocket.NotificationPusher;
import com.socialnetwork.notification_service.model.Notification;
import com.socialnetwork.notification_service.model.UserCache;
import com.socialnetwork.notification_service.repository.NotificationRepository;
import com.socialnetwork.notification_service.service.UserCacheService;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/** Business rules of {@link NotificationServiceImpl}; no Spring context. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

  private static final Long ACTOR_ID = 1L;
  private static final Long RECEIVER_ID = 2L;
  private static final Long TARGET_ID = 30L;
  private static final Long POST_ID = 20L;
  private static final String EVENT_ID = "evt-1";

  @Mock private NotificationRepository notificationRepository;
  @Mock private UserCacheService userCacheService;
  @Mock private NotificationPusher notificationPusher;

  @InjectMocks private NotificationServiceImpl service;

  private static UserCache actor() {
    return UserCache.builder().id(ACTOR_ID).displayName("Actor").avatarUrl("a.png").build();
  }

  private static NotificationEvent event(NotificationType type) {
    return new NotificationEvent(EVENT_ID, ACTOR_ID, RECEIVER_ID, type, TARGET_ID, POST_ID);
  }

  static Stream<Arguments> renderings() {
    return Stream.of(
        Arguments.of(NotificationType.REACT_POST, "đã thích bài viết của bạn.", "/posts/20"),
        Arguments.of(
            NotificationType.REACT_COMMENT,
            "đã thích bình luận của bạn.",
            "/posts/20?comment=30"),
        Arguments.of(
            NotificationType.COMMENT_POST,
            "đã bình luận về bài viết của bạn.",
            "/posts/20?comment=30"),
        Arguments.of(
            NotificationType.REPLY_COMMENT,
            "đã trả lời bình luận của bạn.",
            "/posts/20?reply=30"),
        Arguments.of(NotificationType.FRIEND_REQUEST, "đã gửi lời mời kết bạn.", "/user/1"),
        Arguments.of(
            NotificationType.FRIEND_ACCEPT,
            "đã chấp nhận lời mời kết bạn của bạn.",
            "/user/1"),
        Arguments.of(NotificationType.MESSAGE, "đã gửi cho bạn một tin nhắn.", "/chat/30"));
  }

  @ParameterizedTest(name = "{0} renders \"{1}\" linking to {2}")
  @MethodSource("renderings")
  @DisplayName("content and deep link are built once, per notification type")
  void createNotification_buildsContentAndLinkPerType(
      NotificationType type, String content, String link) {
    when(notificationRepository.existsByEventId(EVENT_ID)).thenReturn(false);
    when(userCacheService.getOrFetch(ACTOR_ID)).thenReturn(actor());
    when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

    service.createNotification(event(type));

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).save(captor.capture());
    Notification saved = captor.getValue();
    assertThat(saved.getContent()).isEqualTo(content);
    assertThat(saved.getLink()).isEqualTo(link);
    assertThat(saved.getType()).isEqualTo(type);
    assertThat(saved.getEventId()).isEqualTo(EVENT_ID);
    assertThat(saved.getReceiverId()).isEqualTo(RECEIVER_ID);
    assertThat(saved.isRead()).isFalse();
    verify(notificationPusher).pushNotification(eq(RECEIVER_ID), any(NotificationDto.class));
  }

  @Test
  @DisplayName("a user never notifies themselves")
  void createNotification_skipsSelfNotification() {
    service.createNotification(
        new NotificationEvent(EVENT_ID, 5L, 5L, NotificationType.REACT_POST, TARGET_ID, POST_ID));

    verifyNoInteractions(notificationRepository, userCacheService, notificationPusher);
  }

  @Test
  @DisplayName("a redelivered eventId is skipped instead of creating a duplicate")
  void createNotification_deduplicatesByEventId() {
    when(notificationRepository.existsByEventId(EVENT_ID)).thenReturn(true);

    service.createNotification(event(NotificationType.REACT_POST));

    verify(notificationRepository, never()).save(any());
    verifyNoInteractions(userCacheService, notificationPusher);
  }

  @Test
  @DisplayName("a cache miss goes through UserCacheService before the notification is stored")
  void createNotification_fetchesActorOnCacheMiss() {
    when(notificationRepository.existsByEventId(EVENT_ID)).thenReturn(false);
    when(userCacheService.getOrFetch(ACTOR_ID)).thenReturn(actor());
    when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

    service.createNotification(event(NotificationType.FRIEND_REQUEST));

    verify(userCacheService).getOrFetch(ACTOR_ID);
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).save(captor.capture());
    assertThat(captor.getValue().getActor().getId()).isEqualTo(ACTOR_ID);
  }

  @Test
  @DisplayName("a failed actor lookup propagates so the record is retried or dead-lettered")
  void createNotification_propagatesActorLookupFailure() {
    when(notificationRepository.existsByEventId(EVENT_ID)).thenReturn(false);
    when(userCacheService.getOrFetch(ACTOR_ID))
        .thenThrow(new ResourceNotFoundException("user-service is down"));

    assertThatThrownBy(() -> service.createNotification(event(NotificationType.REACT_POST)))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(notificationRepository, never()).save(any());
    verifyNoInteractions(notificationPusher);
  }

  @Test
  @DisplayName("events with a null id are rejected with IllegalArgumentException (not retryable)")
  void createNotification_rejectsNullIds() {
    assertThatThrownBy(
            () ->
                service.createNotification(
                    new NotificationEvent(
                        null, ACTOR_ID, RECEIVER_ID, NotificationType.REACT_POST, 1L, 1L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.createNotification(
                    new NotificationEvent(
                        EVENT_ID, null, RECEIVER_ID, NotificationType.REACT_POST, 1L, 1L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.createNotification(
                    new NotificationEvent(
                        EVENT_ID, ACTOR_ID, null, NotificationType.REACT_POST, 1L, 1L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.createNotification(
                    new NotificationEvent(EVENT_ID, ACTOR_ID, RECEIVER_ID, null, 1L, 1L)))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(notificationRepository, userCacheService, notificationPusher);
  }

  @Test
  @DisplayName("one MESSAGE notification per recipient, sender excluded, linking to the room")
  void createMessageNotifications_onePerRecipient() {
    when(notificationRepository.existsByEventId(anyString())).thenReturn(false);
    when(userCacheService.getOrFetch(ACTOR_ID)).thenReturn(actor());
    when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

    service.createMessageNotifications(
        new MessageNotificationEvent(
            "msg-9", 7L, ACTOR_ID, "Actor", "hello", List.of(ACTOR_ID, 2L, 3L)));

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .allSatisfy(
            n -> {
              assertThat(n.getType()).isEqualTo(NotificationType.MESSAGE);
              assertThat(n.getLink()).isEqualTo("/chat/7");
            })
        .extracting(Notification::getReceiverId)
        .containsExactly(2L, 3L);
    assertThat(captor.getAllValues())
        .extracting(Notification::getEventId)
        .containsExactly("msg-9:2", "msg-9:3");
  }

  @Test
  @DisplayName("reading someone else's notification is refused")
  void markAsRead_rejectsForeignNotification() {
    Notification other = Notification.builder().receiverId(99L).build();
    when(notificationRepository.findById(5L)).thenReturn(Optional.of(other));

    assertThatThrownBy(() -> service.markAsRead(RECEIVER_ID, 5L))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("an unknown notification id is a 404")
  void markAsRead_missingNotification() {
    when(notificationRepository.findById(5L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.markAsRead(RECEIVER_ID, 5L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("marking as read flips the flag and pushes the refreshed counter")
  void markAsRead_marksAndPushesCounter() {
    Notification own =
        Notification.builder()
            .receiverId(RECEIVER_ID)
            .actor(actor())
            .type(NotificationType.REACT_POST)
            .read(false)
            .build();
    when(notificationRepository.findById(5L)).thenReturn(Optional.of(own));
    when(notificationRepository.countByReceiverIdAndReadFalse(RECEIVER_ID)).thenReturn(3L);

    NotificationDto dto = service.markAsRead(RECEIVER_ID, 5L);

    assertThat(own.isRead()).isTrue();
    assertThat(dto.read()).isTrue();
    verify(notificationPusher).pushUnreadCount(RECEIVER_ID, 3L);
  }

  @Test
  @DisplayName("marking everything as read pushes a zero counter")
  void markAllAsRead_pushesZero() {
    when(notificationRepository.markAllAsRead(RECEIVER_ID)).thenReturn(4);

    service.markAllAsRead(RECEIVER_ID);

    verify(notificationPusher).pushUnreadCount(RECEIVER_ID, 0L);
  }
}
