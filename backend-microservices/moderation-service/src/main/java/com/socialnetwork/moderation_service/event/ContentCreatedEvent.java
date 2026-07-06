package com.socialnetwork.moderation_service.event;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import vo.TargetType;

@Data
@AllArgsConstructor
public class ContentCreatedEvent {
  private Long targetId;
  private TargetType targetType; // POST or COMMENT
  private String content;
  private Long authorId;
  private List<Map<String, String>> media; // List of media (url, type)
}
