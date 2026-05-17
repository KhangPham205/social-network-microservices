package com.socialnetwork.moderation_service.dto.external;

import java.time.Instant;
import lombok.Data;

@Data
public class UserExternalDto {
  private Long id;
  private String username;
  private String email;
  private String displayName;
  private String avatarUrl;
  private String status;
  private String bio;
  private Instant createdAt;
  private Instant lastActiveAt;
}
