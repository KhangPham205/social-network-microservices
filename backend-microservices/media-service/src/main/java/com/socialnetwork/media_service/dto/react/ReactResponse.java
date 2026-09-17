package com.socialnetwork.media_service.dto.react;

import lombok.*;
import vo.TargetType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactResponse {
  private Long targetId;
  private TargetType targetType;
  private long reactCount;
  private String message;
}
