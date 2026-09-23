package com.socialnetwork.chat_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ConversationResponse {

  private Long id;

  @JsonProperty("isGroup")
  private boolean isGroup;

  private String title;
  private String mediaUrl;
  private Instant createdAt;
  private List<Long> memberIds;
}
