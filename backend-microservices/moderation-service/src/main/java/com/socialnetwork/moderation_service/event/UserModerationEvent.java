package com.socialnetwork.moderation_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserModerationEvent {
  private Long userId;
  private String newStatus;
  private String reason;
}
