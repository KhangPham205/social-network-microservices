package com.socialnetwork.chat_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.socialnetwork.chat_service.enums.ChatLabel;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ConversationSummaryResponse {

  private Long id;
  private String title;
  private String mediaUrl;

  @JsonProperty("isGroup")
  private boolean isGroup;

  private MessageResponse lastMessage;
  private List<ParticipantDto> participants;
  private Set<ChatLabel> labels;
  private Instant updatedAt;
}
