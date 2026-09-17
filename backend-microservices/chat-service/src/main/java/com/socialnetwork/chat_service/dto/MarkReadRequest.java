package com.socialnetwork.chat_service.dto;

import lombok.Data;

@Data
public class MarkReadRequest {
  private Long conversationId;
  private String messageId;
}
