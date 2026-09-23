package com.socialnetwork.chat_service.dto;

import com.socialnetwork.chat_service.enums.ConversationRole;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ParticipantDto {

  private Long id;
  private String displayName;
  private String avatarUrl;
  private ConversationRole role;
}
