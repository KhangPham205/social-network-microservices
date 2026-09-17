package com.socialnetwork.media_service.dto.post;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostSyncDto {
  private Long id;
  private String content;
  private Long authorId;
}
