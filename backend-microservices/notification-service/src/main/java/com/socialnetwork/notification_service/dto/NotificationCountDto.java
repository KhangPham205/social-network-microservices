package com.socialnetwork.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NotificationCountDto {
  private long unreadCount;
}
