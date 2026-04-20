package com.socialnetwork.chat_service.dto;

import java.util.List;
import java.util.Map;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRequest {
  private Long conversationId;
  private String content;
  private Long replyToId;

  // Có thể gửi nhiều file
  private List<Map<String, Object>> mediaAttachments;
}
