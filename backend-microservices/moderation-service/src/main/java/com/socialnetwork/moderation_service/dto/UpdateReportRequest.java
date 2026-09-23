package com.socialnetwork.moderation_service.dto;

import com.socialnetwork.moderation_service.enums.ReportStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

  @NotEmpty private List<Long> reportIds;

  @NotNull private ReportStatus reportStatus;
}
