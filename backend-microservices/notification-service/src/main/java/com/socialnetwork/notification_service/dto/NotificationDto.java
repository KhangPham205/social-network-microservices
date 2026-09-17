package com.socialnetwork.notification_service.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationDto {
  private Long id;
  private ActorDto actor;
  private String content;
  private String link;
  private boolean isRead;
  private Instant createdAt;
}
