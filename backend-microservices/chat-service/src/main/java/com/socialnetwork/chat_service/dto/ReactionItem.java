package com.socialnetwork.chat_service.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionItem {
  private Long userId;
  private String emoji;
  private Instant reactedAt;
}
