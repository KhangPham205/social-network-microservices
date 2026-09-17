package com.socialnetwork.moderation_service.dto.external;

import java.time.Instant;
import lombok.Data;

@Data
public class PostExternalDto {
  private Long id;
  private Long authorId;
  private String content;
  private String mediaUrl;
  private String mediaType;
  private Instant createdAt;
  private Instant updatedAt;
}
