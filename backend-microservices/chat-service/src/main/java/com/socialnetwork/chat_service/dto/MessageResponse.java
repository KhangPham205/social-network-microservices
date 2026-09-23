package com.socialnetwork.chat_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.socialnetwork.chat_service.enums.MessageType;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Wire representation of a message, used by REST responses and WebSocket broadcasts alike. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {

  private String id;
  private Long conversationId;
  private Long senderId;
  private String senderName;
  private String senderAvatar;
  private String replyToId;
  private String content;
  private MessageType type;
  private List<MediaItem> media;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Instant createdAt;

  private List<Long> readBy;
  private boolean deleted;
  private Instant deletedAt;
}
