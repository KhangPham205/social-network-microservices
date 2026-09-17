package com.socialnetwork.chat_service.dto;

import java.util.List;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRequest {
  private Long conversationId;
  private String content;
  private Long replyToId;

  // Có thể gửi nhiều file
  private List<MultipartFile> mediaFiles;
}
