package com.socialnetwork.chat_service.dto;

import lombok.Data;

@Data
public class UpdateConversationRequest {
  private Long conversationId;
  private String title;
  private String mediaUrl;
}
