package com.socialnetwork.chat_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationCreateRequest {

  @JsonProperty("isGroup")
  private boolean isGroup;

  @Size(max = 255)
  private String title;

  @Size(max = 2048)
  private String mediaUrl;

  /** Members besides the creator. Exactly one for a private room. */
  @NotEmpty
  @Size(max = 200)
  private List<Long> memberIds;
}
