package com.socialnetwork.chat_service.dto;

import java.util.List;
import lombok.Data;

@Data
public class AddMembersRequest {
  private Long conversationId;
  private List<Long> userIds;
}
