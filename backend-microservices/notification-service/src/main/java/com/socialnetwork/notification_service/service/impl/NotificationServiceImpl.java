package com.socialnetwork.notification_service.service.impl;

import com.socialnetwork.notification_service.dto.ActorDto;
import com.socialnetwork.notification_service.dto.NotificationCountDto;
import com.socialnetwork.notification_service.dto.NotificationDto;
import com.socialnetwork.notification_service.enums.NotificationType;
import com.socialnetwork.notification_service.model.Notification;
import com.socialnetwork.notification_service.model.UserCache;
import com.socialnetwork.notification_service.repository.NotificationRepository;
import com.socialnetwork.notification_service.repository.UserCacheRepository;
import com.socialnetwork.notification_service.service.NotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vo.PageVO;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {
  private final NotificationRepository notificationRepository;
  private final UserCacheRepository userCacheRepository;
  private final SimpMessagingTemplate messagingTemplate;

  @Override
  @Transactional
  public void sendNotification(
      UserCache actor, Long receiverId, NotificationType type, Long targetId, Long postId) {

    if (actor.getId().equals(receiverId)) {
      return; // Không gửi notification cho chính mình
    }

    String content = "";
    String link = "";

    switch (type) {
      case REACT_POST:
        content = "đã thích bài viết của bạn.";
        link = "/posts/" + postId;
        break;
      case COMMENT_POST:
        content = "đã bình luận về bài viết của bạn.";
        link = "/posts/" + postId + "?comment=" + targetId;
        break;
      case REPLY_COMMENT:
        content = "đã trả lời bình luận của bạn.";
        link = "/posts/" + postId + "?reply=" + targetId;
        break;
      case REACT_COMMENT:
        content = "đã thích bình luận của bạn.";
        link = "/posts/" + postId + "?comment=" + targetId;
        break;
      case FRIEND_REQUEST:
        content = "đã gửi cho bạn một lời mời kết bạn.";
        link = "/users/" + actor.getId();
        break;
      case FRIEND_ACCEPT:
        content = "đã chấp nhận lời mời kết bạn của bạn.";
        link = "/users/" + actor.getId();
        break;
      default:
        content = "có một hoạt động mới.";
        link = "";
        break;
    }

    Notification notification =
        Notification.builder()
            .receiverId(receiverId)
            .actor(actor)
            .type(type)
            .content(content)
            .link(link)
            .isRead(false)
            .build();
    notificationRepository.save(notification);
    log.info("✅ Notification created for user: {}", receiverId);

    // Push qua WebSocket
    NotificationDto dto = toDto(notification);
    messagingTemplate.convertAndSendToUser(receiverId.toString(), "/queue/notifications", dto);

    // Push số thông báo chưa đọc
    long unreadCount = notificationRepository.countByReceiverIdAndIsReadFalse(receiverId);
    messagingTemplate.convertAndSendToUser(
        receiverId.toString(),
        "/queue/notification-summary",
        new NotificationCountDto(unreadCount));
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<NotificationDto> getMyNotifications(Long userId, Pageable pageable) {
    Page<Notification> notificationPage =
        notificationRepository.findByReceiverIdOrderByCreatedAtDesc(userId, pageable);

    List<NotificationDto> content =
        notificationPage.getContent().stream().map(this::toDto).toList();

    return PageVO.<NotificationDto>builder()
        .page(notificationPage.getNumber())
        .size(notificationPage.getSize())
        .totalElements(notificationPage.getTotalElements())
        .totalPages(notificationPage.getTotalPages())
        .numberOfElements(content.size())
        .content(content)
        .build();
  }

  @Override
  @Transactional
  public NotificationDto markAsRead(Long userId, Long notificationId) {
    Notification notification =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new RuntimeException("Notification not found"));

    if (!notification.getReceiverId().equals(userId)) {
      throw new RuntimeException("You do not have permission to access this notification");
    }

    if (!notification.isRead()) {
      notification.setRead(true);
      notificationRepository.save(notification);
      log.info("✅ Marked notification {} as read", notificationId);

      long unreadCount = notificationRepository.countByReceiverIdAndIsReadFalse(userId);
      messagingTemplate.convertAndSendToUser(
          userId.toString(), "/queue/notification-summary", new NotificationCountDto(unreadCount));
    }

    return toDto(notification);
  }

  @Override
  @Transactional
  public void markAllAsRead(Long userId) {
    notificationRepository.markAllAsRead(userId);
    log.info("✅ Marked all notifications as read for user: {}", userId);

    messagingTemplate.convertAndSendToUser(
        userId.toString(), "/queue/notification-summary", new NotificationCountDto(0L));
  }

  /** Helper để convert Entity -> DTO */
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
}
