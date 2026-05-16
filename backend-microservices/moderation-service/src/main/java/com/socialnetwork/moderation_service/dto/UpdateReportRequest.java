package com.socialnetwork.moderation_service.dto;

import com.socialnetwork.moderation_service.enums.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateReportRequest {
    private List<Long> reportIds;
    private ReportStatus reportStatus;
}
