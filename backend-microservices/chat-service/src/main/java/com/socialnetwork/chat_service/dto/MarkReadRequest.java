package com.socialnetwork.chat_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarkReadRequest {

  @NotNull private Long conversationId;

  @NotBlank
  @Size(max = 64)
  private String messageId;
}
