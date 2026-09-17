package com.socialnetwork.user_service.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserModerationDto {
  private Long id;
  private String displayName;
  private String avatarUrl;
  private String bio;
  private Instant createdAt;
  private Instant lastActiveAt;
}
