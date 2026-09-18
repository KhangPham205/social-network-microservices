package com.socialnetwork.notification_service.service;

import com.socialnetwork.common.events.MessageNotificationEvent;
import com.socialnetwork.common.events.NotificationEvent;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.notification_service.dto.NotificationCountDto;
import com.socialnetwork.notification_service.dto.NotificationDto;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

  /**
   * Persists and pushes the notification described by a {@link NotificationEvent}. Self
   * notifications and already processed {@code eventId}s are ignored.
   *
   * @throws IllegalArgumentException when mandatory fields are missing (not retried)
   */
  void createNotification(NotificationEvent event);

  /** One MESSAGE notification per recipient of a chat message, linking to the room. */
  void createMessageNotifications(MessageNotificationEvent event);

  PageVO<NotificationDto> getMyNotifications(Long userId, Pageable pageable);

  NotificationCountDto getUnreadCount(Long userId);

  NotificationDto markAsRead(Long userId, Long notificationId);

  void markAllAsRead(Long userId);
}
