package com.socialnetwork.moderation_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import com.socialnetwork.common.vo.TargetType;

@Data
@AllArgsConstructor
public class ContentModerationEvent {
  private String targetId;
  private TargetType targetType;
  private String action; // "BLOCK" hoặc "UNBLOCK"
}
