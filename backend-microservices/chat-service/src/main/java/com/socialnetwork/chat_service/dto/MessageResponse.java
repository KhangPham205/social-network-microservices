// MessageResponse.java (đã tương thích với jsonb map)
package com.socialnetwork.chat_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {
  private String id;
  private Long conversationId;
  private Long senderId;
  private String senderName;
  private String senderAvatar;
  private Long replyToId;
  private String content;
  private List<Map<String, Object>> media; // media as list of maps { url, type, ... }

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Instant createdAt;

  private Boolean isRead;
  private List<Map<String, Object>> reactions; // list of reaction objects
  private Instant deletedAt;
}
