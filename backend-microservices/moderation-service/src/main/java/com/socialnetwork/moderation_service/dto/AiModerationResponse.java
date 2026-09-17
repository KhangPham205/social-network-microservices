package com.socialnetwork.moderation_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiModerationResponse {

  @JsonProperty("is_toxic")
  private boolean isToxic;

  @JsonProperty("score")
  private Double confidenceScore;

  @JsonProperty("reason")
  private String reason;
}
