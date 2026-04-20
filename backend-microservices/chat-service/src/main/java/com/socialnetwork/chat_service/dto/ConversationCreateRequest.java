package com.socialnetwork.chat_service.dto;

import java.util.List;
import lombok.Data;

@Data
public class ConversationCreateRequest {
  private Boolean isGroup;
  private String title;
  private String mediaUrl;
  private List<Long> memberIds; // danh sách id user trong nhóm
}
