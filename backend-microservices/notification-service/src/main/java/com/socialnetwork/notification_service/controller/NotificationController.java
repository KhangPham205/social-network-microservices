package com.socialnetwork.notification_service.controller;

import com.socialnetwork.notification_service.dto.NotificationDto;
import com.socialnetwork.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vo.PageVO;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;

  // Helper để lấy userId từ SecurityContext
  private Long getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      return Long.parseLong(authentication.getName());
    }
    throw new RuntimeException("Unauthorized");
  }

  // 1. Lấy danh sách thông báo
  @GetMapping
  public ResponseEntity<PageVO<NotificationDto>> getMyNotifications(
      @ParameterObject Pageable pageable) {
    Long userId = getCurrentUserId();
    log.info("Fetching notifications for user: {}", userId);
    return ResponseEntity.ok(notificationService.getMyNotifications(userId, pageable));
  }

  // 2. Đánh dấu 1 thông báo là đã đọc
  @PutMapping("/{id}/read")
  public ResponseEntity<NotificationDto> markAsRead(@PathVariable("id") Long id) {
    Long userId = getCurrentUserId();
    log.info("Marking notification {} as read for user: {}", id, userId);
    return ResponseEntity.ok(notificationService.markAsRead(userId, id));
  }

  // 3. Đánh dấu TẤT CẢ là đã đọc
  @PutMapping("/read-all")
  public ResponseEntity<Void> markAllAsRead() {
    Long userId = getCurrentUserId();
    log.info("Marking all notifications as read for user: {}", userId);
    notificationService.markAllAsRead(userId);
    return ResponseEntity.noContent().build();
  }
}
