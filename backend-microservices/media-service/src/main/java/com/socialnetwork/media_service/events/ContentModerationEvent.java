package com.socialnetwork.media_service.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentModerationEvent {
  private String targetId;
  private String targetType;
  private String action;
}
