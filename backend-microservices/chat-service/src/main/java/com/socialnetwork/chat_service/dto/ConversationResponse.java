package com.socialnetwork.chat_service.dto;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationResponse {
  private Long id;
  private Boolean isGroup;
  private String title;
  private String mediaUrl;
  private Instant createdAt;
  private List<Long> memberIds;
}
