package com.socialnetwork.notification_service.service.impl;

import com.socialnetwork.common.events.MessageNotificationEvent;
import com.socialnetwork.common.events.NotificationEvent;
import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.vo.NotificationType;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.notification_service.dto.ActorDto;
import com.socialnetwork.notification_service.dto.NotificationCountDto;
import com.socialnetwork.notification_service.dto.NotificationDto;
import com.socialnetwork.notification_service.infra.websocket.NotificationPusher;
import com.socialnetwork.notification_service.model.Notification;
import com.socialnetwork.notification_service.model.UserCache;
import com.socialnetwork.notification_service.repository.NotificationRepository;
import com.socialnetwork.notification_service.service.NotificationService;
import com.socialnetwork.notification_service.service.UserCacheService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

  private final NotificationRepository notificationRepository;
  private final UserCacheService userCacheService;
  private final NotificationPusher notificationPusher;

  /** Content (Vietnamese, rendered after the actor's display name) and deep link per type. */
  record Rendering(String content, String link) {}

  @Override
  @Transactional
  public void createNotification(NotificationEvent event) {
    requireNonNull(event.eventId(), "NotificationEvent.eventId");
    requireNonNull(event.actorId(), "NotificationEvent.actorId");
    requireNonNull(event.receiverId(), "NotificationEvent.receiverId");
    requireNonNull(event.type(), "NotificationEvent.type");

    if (event.actorId().equals(event.receiverId())) {
      log.debug("Skipping self notification {} for user {}", event.eventId(), event.actorId());
      return;
    }
    if (notificationRepository.existsByEventId(event.eventId())) {
      log.info("Notification event {} already processed, skipping", event.eventId());
      return;
    }

    UserCache actor = userCacheService.getOrFetch(event.actorId());
    Rendering rendering = render(event);
    persistAndPush(event.eventId(), event.receiverId(), actor, event.type(), rendering);
  }

  @Override
  @Transactional
  public void createMessageNotifications(MessageNotificationEvent event) {
    requireNonNull(event.messageId(), "MessageNotificationEvent.messageId");
    requireNonNull(event.roomId(), "MessageNotificationEvent.roomId");
    requireNonNull(event.senderId(), "MessageNotificationEvent.senderId");
    requireNonNull(event.recipientIds(), "MessageNotificationEvent.recipientIds");

    Rendering rendering =
        new Rendering("đã gửi cho bạn một tin nhắn.", "/chat/" + event.roomId());
    UserCache actor = null;

    for (Long recipientId : event.recipientIds()) {
      if (recipientId == null || recipientId.equals(event.senderId())) {
        continue;
      }
      String eventId = event.messageId() + ":" + recipientId;
      if (notificationRepository.existsByEventId(eventId)) {
        log.info("Message notification {} already processed, skipping", eventId);
        continue;
      }
      if (actor == null) {
        actor = userCacheService.getOrFetch(event.senderId());
      }
      persistAndPush(eventId, recipientId, actor, NotificationType.MESSAGE, rendering);
    }
  }

  private void persistAndPush(
      String eventId, Long receiverId, UserCache actor, NotificationType type, Rendering rendering) {
    Notification notification =
        notificationRepository.save(
            Notification.builder()
                .eventId(eventId)
                .receiverId(receiverId)
                .actor(actor)
                .type(type)
                .content(rendering.content())
                .link(rendering.link())
                .read(false)
                .build());
    log.info(
        "Notification {} ({}) saved for user {} from actor {}",
        notification.getId(),
        type,
        receiverId,
        actor.getId());

    notificationPusher.pushNotification(receiverId, toDto(notification));
    notificationPusher.pushUnreadCount(
        receiverId, notificationRepository.countByReceiverIdAndReadFalse(receiverId));
  }

  static Rendering render(NotificationEvent event) {
    return switch (event.type()) {
      case REACT_POST -> new Rendering("đã thích bài viết của bạn.", "/posts/" + event.postId());
      case REACT_COMMENT ->
          new Rendering(
              "đã thích bình luận của bạn.",
              "/posts/" + event.postId() + "?comment=" + event.targetId());
      case COMMENT_POST ->
          new Rendering(
              "đã bình luận về bài viết của bạn.",
              "/posts/" + event.postId() + "?comment=" + event.targetId());
      case REPLY_COMMENT ->
          new Rendering(
              "đã trả lời bình luận của bạn.",
              "/posts/" + event.postId() + "?reply=" + event.targetId());
      case FRIEND_REQUEST ->
          new Rendering("đã gửi lời mời kết bạn.", "/user/" + event.actorId());
      case FRIEND_ACCEPT ->
          new Rendering("đã chấp nhận lời mời kết bạn của bạn.", "/user/" + event.actorId());
      case MESSAGE -> new Rendering("đã gửi cho bạn một tin nhắn.", "/chat/" + event.targetId());
    };
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<NotificationDto> getMyNotifications(Long userId, Pageable pageable) {
    // Ordering is fixed (newest first); client-supplied sort is ignored.
    Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    Page<Notification> notifications =
        notificationRepository.findByReceiverIdOrderByCreatedAtDesc(userId, page);
    return PageVO.from(notifications, this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public NotificationCountDto getUnreadCount(Long userId) {
    return new NotificationCountDto(notificationRepository.countByReceiverIdAndReadFalse(userId));
  }

  @Override
  @Transactional
  public NotificationDto markAsRead(Long userId, Long notificationId) {
    Notification notification =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Notification " + notificationId + " not found"));

    if (!Objects.equals(notification.getReceiverId(), userId)) {
      throw new AccessDeniedException("Notification does not belong to the current user");
    }

    if (!notification.isRead()) {
      notification.setRead(true);
      notificationRepository.flush();
      notificationPusher.pushUnreadCount(
          userId, notificationRepository.countByReceiverIdAndReadFalse(userId));
    }
    return toDto(notification);
  }

  @Override
  @Transactional
  public void markAllAsRead(Long userId) {
    int updated = notificationRepository.markAllAsRead(userId);
    log.info("Marked {} notifications as read for user {}", updated, userId);
    notificationPusher.pushUnreadCount(userId, 0L);
  }

  private NotificationDto toDto(Notification n) {
    UserCache actor = n.getActor();
    return new NotificationDto(
        n.getId(),
        new ActorDto(actor.getId(), actor.getDisplayName(), actor.getAvatarUrl()),
        n.getContent(),
        n.getLink(),
        n.isRead(),
        n.getCreatedAt());
  }

  private static void requireNonNull(Object value, String field) {
    if (value == null) {
      throw new IllegalArgumentException(field + " is required");
    }
  }
}
