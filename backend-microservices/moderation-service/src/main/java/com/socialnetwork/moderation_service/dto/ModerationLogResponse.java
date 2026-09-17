package com.socialnetwork.moderation_service.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import vo.TargetType;

@Data
@Builder
public class ModerationLogResponse {
  private Long id;
  private TargetType targetType; // POST, COMMENT, USER
  private String targetId; // ID of moderated object
  private String action; // AUTO_BAN, ADMIN_BAN, ADMIN_RESTORE
  private String reason; // Reason

  // Actor information
  private Long actorId;
  private String actorName; // null -> "System (AI)"
  private String actorAvatar;

  private Instant createdAt;
}
