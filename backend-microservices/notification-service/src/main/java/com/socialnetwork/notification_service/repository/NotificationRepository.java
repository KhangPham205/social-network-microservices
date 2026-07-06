package com.socialnetwork.notification_service.repository;

import com.socialnetwork.notification_service.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository
    extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
  // Tìm thông báo cho một user, sắp xếp mới nhất
  Page<Notification> findByReceiverIdOrderByCreatedAtDesc(Long receiverId, Pageable pageable);

  // Đếm số thông báo chưa đọc
  long countByReceiverIdAndIsReadFalse(Long receiverId);

  // Đánh dấu tất cả là đã đọc
  @Modifying
  @Query(
      "UPDATE Notification n SET n.isRead = true WHERE n.receiverId = :receiverId AND n.isRead = false")
  void markAllAsRead(@Param("receiverId") Long receiverId);
}
