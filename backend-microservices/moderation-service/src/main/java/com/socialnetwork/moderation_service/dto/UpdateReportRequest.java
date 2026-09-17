package com.socialnetwork.moderation_service.dto;

import com.socialnetwork.moderation_service.enums.ReportStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateReportRequest {
  private List<Long> reportIds;
  private ReportStatus reportStatus;
}
