package com.socialnetwork.media_service.dto.react;

import lombok.*;
import com.socialnetwork.common.vo.TargetType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactRequest {
  private Long targetId;
  private TargetType targetType; // "POST", "COMMENT", etc.
  private Long reactTypeId;
}
