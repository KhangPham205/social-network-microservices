package com.socialnetwork.moderation_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import vo.TargetType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateComplaintRequest {
  private String targetId;
  private TargetType targetType;
  private String reason;
}
