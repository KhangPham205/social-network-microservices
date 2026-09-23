package com.socialnetwork.moderation_service.dto;

import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.enums.ModerationLogAction;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/** One row of the moderation audit trail. */
@Data
@Builder
public class ModerationLogResponse {
  private Long id;
  private TargetType targetType;
  private String targetId;
  private ModerationLogAction action;
  private String reason;

  private Long actorId;
  /** Display name of the actor, or {@code System (AI)} for automated decisions. */
  private String actorName;

  private Long reportId;
  private Long complaintId;

  private Instant createdAt;
}
