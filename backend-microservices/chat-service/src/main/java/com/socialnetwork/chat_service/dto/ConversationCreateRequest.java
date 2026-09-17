package com.socialnetwork.chat_service.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationCreateRequest {
  private Boolean isGroup;
  private String title;
  private String mediaUrl;
  private List<Long> memberIds; // danh sách id user trong nhóm
}
