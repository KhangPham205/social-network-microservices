package com.socialnetwork.chat_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body of both {@code POST /api/v1/chat/messages} and the STOMP {@code /app/chat.send} frame. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRequest {

  @NotNull private Long conversationId;

  @Size(max = 5000)
  private String content;

  /** Mongo id of the message being replied to. */
  @Size(max = 64)
  private String replyToId;

  @Valid
  @Size(max = 10)
  private List<MediaItem> media;
}
