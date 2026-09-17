package com.socialnetwork.moderation_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import vo.TargetType;

@Data
@AllArgsConstructor
public class ContentModerationEvent {
  private String targetId;
  private TargetType targetType;
  private String action; // "BLOCK" hoặc "UNBLOCK"
}
