package com.socialnetwork.moderation_service.dto;

import com.socialnetwork.moderation_service.enums.AccountStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModerationUserDetailResponse {
  private Long id;
  private String displayName;
  private String avatarUrl;
  private String email; // Thông tin riêng tư
  private String bio;
  private AccountStatus status; // ACTIVE/BLOCKED
  private Long violationCount; // Số lần bị báo cáo thành công (Approved Reports)
  private Instant createdAt;
  private Instant lastActiveAt;
}
