package com.socialnetwork.chat_service.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddMembersRequest {

  @NotNull private Long conversationId;

  @NotEmpty
  @Size(max = 100)
  private List<Long> userIds;
}
