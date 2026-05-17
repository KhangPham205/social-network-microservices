package com.socialnetwork.moderation_service.dto;

import com.socialnetwork.moderation_service.enums.ComplaintStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vo.TargetType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintResponse {
  private Long id;
  private String targetId;
  private TargetType targetType;
  private Long userId;
  private String userDisplayName;
  private String content; // Reason for complaint
  private ComplaintStatus status;
  private Instant createdAt;
  private Instant updatedAt;
}
