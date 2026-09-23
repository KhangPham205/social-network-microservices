package com.socialnetwork.moderation_service.dto;

import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.enums.ReportReason;
import com.socialnetwork.moderation_service.enums.ReportSource;
import com.socialnetwork.moderation_service.enums.ReportStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReportResponse {
  private Long id;
  private TargetType targetType;
  private String targetId;

  /** Null when {@code source} is {@code SYSTEM}. */
  private Long reporterId;

  private String reporterName;
  private String reporterAvatar;

  private ReportSource source;
  private ReportReason reason;
  private String customReason;
  private ReportStatus status;
  private Boolean isBannedBySystem;
  private Instant createdAt;
}
