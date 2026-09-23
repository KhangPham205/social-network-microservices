package com.socialnetwork.chat_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateConversationRequest {

  @NotNull private Long conversationId;

  @Size(max = 255)
  private String title;

  @Size(max = 2048)
  private String mediaUrl;
}
