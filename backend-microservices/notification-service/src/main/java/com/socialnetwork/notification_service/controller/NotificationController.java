package com.socialnetwork.notification_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.security.SecurityUtils;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.notification_service.dto.NotificationCountDto;
import com.socialnetwork.notification_service.dto.NotificationDto;
import com.socialnetwork.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.NOTIFICATIONS)
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;

  @GetMapping
  public ResponseEntity<PageVO<NotificationDto>> getMyNotifications(
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(
        notificationService.getMyNotifications(SecurityUtils.getCurrentUserId(), pageable));
  }

  @GetMapping("/unread-count")
  public ResponseEntity<NotificationCountDto> getUnreadCount() {
    return ResponseEntity.ok(notificationService.getUnreadCount(SecurityUtils.getCurrentUserId()));
  }

  @PutMapping("/{id}/read")
  public ResponseEntity<NotificationDto> markAsRead(@PathVariable("id") Long id) {
    return ResponseEntity.ok(notificationService.markAsRead(SecurityUtils.getCurrentUserId(), id));
  }

  @PutMapping("/read-all")
  public ResponseEntity<Void> markAllAsRead() {
    notificationService.markAllAsRead(SecurityUtils.getCurrentUserId());
    return ResponseEntity.noContent().build();
  }
}
