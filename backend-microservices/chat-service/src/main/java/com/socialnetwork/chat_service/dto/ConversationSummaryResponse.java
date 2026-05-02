package com.socialnetwork.chat_service.dto;

import com.socialnetwork.chat_service.enums.ChatLabel;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationSummaryResponse {
  private Long id;
  private String title;
  private String mediaUrl;
  private boolean isGroup;

  private Map<String, Object> lastMessage;
  private List<ParticipantDto> participants;

  private Set<ChatLabel> labels;

  private Instant updatedAt;
}
