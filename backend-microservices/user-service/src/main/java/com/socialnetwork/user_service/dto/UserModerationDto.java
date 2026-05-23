package com.socialnetwork.user_service.dto;

import com.socialnetwork.user_service.model.User;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

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