package com.socialnetwork.notification_service.repository;

import com.socialnetwork.notification_service.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  @EntityGraph(attributePaths = "actor")
  Page<Notification> findByReceiverIdOrderByCreatedAtDesc(Long receiverId, Pageable pageable);

  long countByReceiverIdAndReadFalse(Long receiverId);

  boolean existsByEventId(String eventId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Notification n SET n.read = true WHERE n.receiverId = :receiverId AND n.read = false")
  int markAllAsRead(@Param("receiverId") Long receiverId);
}
