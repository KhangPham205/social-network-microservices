package com.socialnetwork.moderation_service.dto;

import com.socialnetwork.common.vo.AccountStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/** Admin view of one user: profile from user-service, credential from auth-service, local counts. */
@Data
@Builder
public class ModerationUserDetailResponse {
  private Long id;
  private String displayName;
  private String avatarUrl;
  private String email;
  private String bio;
  private AccountStatus status;
  private Long violationCount;
  private Instant createdAt;
  private Instant lastActiveAt;
}
