package com.socialnetwork.media_service.dto.react;

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
  /** Reaction name to count, for example {@code {"LIKE": 10, "LOVE": 3}}. */
  private Map<String, Long> counts;
  private long total;
  /** Reaction of the current viewer, or null. */
  private String currentUserReact;
}
