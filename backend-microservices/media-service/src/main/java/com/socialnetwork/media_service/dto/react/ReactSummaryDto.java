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
  private Map<String, Long> counts; // ví dụ: {"LIKE": 10, "LOVE": 3}
  private long total;
  private String currentUserReact; // ví dụ: "LOVE"
}
