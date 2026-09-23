package com.socialnetwork.chat_service.dto;

import com.socialnetwork.chat_service.enums.ConversationRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMemberRoleRequest {

  @NotNull private Long conversationId;

  @NotNull private Long userIdToChange;

  @NotNull private ConversationRole newRole;
}
