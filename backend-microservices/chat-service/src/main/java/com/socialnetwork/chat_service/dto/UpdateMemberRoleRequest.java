package com.socialnetwork.chat_service.dto;

import com.socialnetwork.chat_service.enums.ConversationRole;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {
  private Long conversationId;
  private Long userIdToChange;
  private ConversationRole newRole;
}
