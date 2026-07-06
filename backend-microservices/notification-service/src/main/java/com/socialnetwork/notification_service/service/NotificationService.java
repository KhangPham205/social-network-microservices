package com.socialnetwork.notification_service.service;

import com.socialnetwork.notification_service.dto.NotificationDto;
import com.socialnetwork.notification_service.enums.NotificationType;
import com.socialnetwork.notification_service.model.UserCache;
import org.springframework.data.domain.Pageable;
import vo.PageVO;

public interface NotificationService {
  /**
   * Tạo và gửi thông báo
   *
   * @param actor Người thực hiện (UserCache)
   * @param receiverId ID người nhận
   * @param type Loại thông báo
   * @param targetId ID của đối tượng (Post, Comment)
   * @param postId ID của bài post gốc (để tạo link)
   */
  void sendNotification(
      UserCache actor, Long receiverId, NotificationType type, Long targetId, Long postId);

  // (Các hàm API)
  PageVO<NotificationDto> getMyNotifications(Long userId, Pageable pageable);

  NotificationDto markAsRead(Long userId, Long notificationId);

  void markAllAsRead(Long userId);
}
