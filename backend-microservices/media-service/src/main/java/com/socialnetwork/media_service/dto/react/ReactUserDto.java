package com.socialnetwork.media_service.dto.react;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactUserDto {
  private Long userId;
  private String displayName;
  private String avatarUrl;
  private Long reactTypeId;
  /** LIKE, LOVE, HAHA, ... */
  private String reactTypeName;
}
