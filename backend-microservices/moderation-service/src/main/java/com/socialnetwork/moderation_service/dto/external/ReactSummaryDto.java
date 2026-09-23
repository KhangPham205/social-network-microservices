package com.socialnetwork.moderation_service.dto.external;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactSummaryDto {
  private Map<String, Long> counts; // e.g. {"LIKE": 10, "LOVE": 3}
  private long total;
  private String currentUserReact; // e.g. "LOVE"
}
