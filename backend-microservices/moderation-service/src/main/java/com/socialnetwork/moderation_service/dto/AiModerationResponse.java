package com.socialnetwork.moderation_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiModerationResponse {
    
    @JsonProperty("is_toxic")
    private boolean isToxic;
    
    @JsonProperty("score")
    private double confidenceScore;
    
    @JsonProperty("reason")
    private String reason;
}
