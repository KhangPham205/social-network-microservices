package com.socialnetwork.moderation_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.socialnetwork.common.vo.TargetType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateComplaintRequest {
  private String targetId;
  private TargetType targetType;
  private String reason;
}
