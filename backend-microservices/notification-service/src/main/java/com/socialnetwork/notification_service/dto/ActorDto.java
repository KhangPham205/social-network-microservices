package com.socialnetwork.notification_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActorDto {
  private Long id;
  private String displayName;
  private String avatarUrl;
}
