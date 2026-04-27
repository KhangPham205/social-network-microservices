package com.socialnetwork.notification_service.handler;

import com.socialnetwork.notification_service.dto.ActorDto;
import com.socialnetwork.notification_service.dto.NotificationCountDto;
import com.socialnetwork.notification_service.dto.NotificationDto;
import com.socialnetwork.notification_service.enums.NotificationType;
import com.socialnetwork.notification_service.model.Notification;
import com.socialnetwork.notification_service.model.UserCache;
import com.socialnetwork.notification_service.repository.NotificationRepository;
import com.socialnetwork.notification_service.repository.UserCacheRepository;
import events.FriendRequestEvent;
import events.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventHandler {

  private final NotificationRepository notificationRepository;
  private final UserCacheRepository userCacheRepository;
  private final SimpMessagingTemplate messagingTemplate;

  @KafkaListener(topics = "notification-topic", groupId = "notification-service-group")
  @Transactional
  public void handleNotificationEvent(NotificationEvent event) {
    if (event.actorId().equals(event.receiverId())) return;

    UserCache actor = userCacheRepository.findById(event.actorId()).orElse(null);
    if (actor == null) {
      log.warn("⚠️ Actor not found in cache: {}", event.actorId());
      return;
    }

    NotificationType type = NotificationType.valueOf(event.type());
    String content = "";
    String link = "";

    switch (type) {
      case REACT_POST -> {
        content = "đã thích bài viết của bạn.";
        link = "/posts/" + event.postId();
      }
      case COMMENT_POST -> {
        content = "đã bình luận về bài viết của bạn.";
        link = "/posts/" + event.postId() + "?comment=" + event.targetId();
      }
      case REPLY_COMMENT -> {
        content = "đã trả lời bình luận của bạn.";
        link = "/posts/" + event.postId() + "?reply=" + event.targetId();
      }
      case REACT_COMMENT -> {
        content = "đã thích bình luận của bạn.";
        link = "/posts/" + event.postId() + "?comment=" + event.targetId();
      }
      case FRIEND_REQUEST -> {
        content = "đã gửi lời mời kết bạn.";
        link = "/users/" + event.actorId();
      }
      case FRIEND_ACCEPT -> {
        content = "đã chấp nhận lời mời kết bạn của bạn.";
        link = "/users/" + event.actorId();
      }
      default -> {
        content = "có một hoạt động mới.";
        link = "";
      }
    }

    Notification notification =
        Notification.builder()
            .receiverId(event.receiverId())
            .actor(actor)
            .type(type)
            .content(content)
            .link(link)
            .isRead(false)
            .build();
    notificationRepository.save(notification);
    log.info(
        "✅ Notification saved for user: {} from actor: {}", event.receiverId(), event.actorId());

    messagingTemplate.convertAndSendToUser(
        event.receiverId().toString(), "/queue/notifications", toDto(notification));

    long unreadCount = notificationRepository.countByReceiverIdAndIsReadFalse(event.receiverId());
    messagingTemplate.convertAndSendToUser(
        event.receiverId().toString(),
        "/queue/notification-summary",
        new NotificationCountDto(unreadCount));
  }

  private NotificationDto toDto(Notification n) {
    return NotificationDto.builder()
        .id(n.getId())
        .actor(
            ActorDto.builder()
                .id(n.getActor().getId())
                .displayName(n.getActor().getDisplayName())
                .avatarUrl(n.getActor().getAvatarUrl())
                .build())
        .content(n.getContent())
        .link(n.getLink())
        .isRead(n.isRead())
        .createdAt(n.getCreatedAt())
        .build();
  }

  @KafkaListener(topics = "friend-request-topic", groupId = "notification-service-friend-group")
  @Transactional
  public void handleFriendRequestEvent(FriendRequestEvent event) {
    try {
      if (event.senderId().equals(event.receiverId())) return;

      log.info(
          "📨 Processing FriendRequestEvent: sender={}, receiver={}",
          event.senderId(),
          event.receiverId());

      UserCache actor = userCacheRepository.findById(event.senderId()).orElse(null);
      if (actor == null) {
        log.warn("⚠️ Actor not found in cache: {}", event.senderId());
        return;
      }

      String content = "đã gửi lời mời kết bạn.";
      String link = "/users/" + event.senderId();

      Notification notification =
          Notification.builder()
              .receiverId(event.receiverId())
              .actor(actor)
              .type(NotificationType.FRIEND_REQUEST)
              .content(content)
              .link(link)
              .isRead(false)
              .build();
      notificationRepository.save(notification);
      log.info(
          "✅ Friend request notification saved for user: {} from: {}",
          event.receiverId(),
          event.senderId());

      // Gửi WebSocket notification tới user
      messagingTemplate.convertAndSendToUser(
          event.receiverId().toString(), "/queue/notifications", toDto(notification));

      // Gửi notification count update
      long unreadCount = notificationRepository.countByReceiverIdAndIsReadFalse(event.receiverId());
      messagingTemplate.convertAndSendToUser(
          event.receiverId().toString(),
          "/queue/notification-summary",
          new NotificationCountDto(unreadCount));
    } catch (Exception e) {
      log.error(
          "❌ Error processing FriendRequestEvent: sender={}, receiver={}, error={}",
          event.senderId(),
          event.receiverId(),
          e.getMessage(),
          e);
    }
  }
}
