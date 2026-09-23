package com.socialnetwork.moderation_service.dto;

import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.enums.ReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateReportRequest {

  @NotNull private TargetType targetType;

  @NotBlank private String targetId;

  @NotNull private ReportReason reason;

  /** Only kept when {@code reason} is {@code OTHER}. */
  private String customReason;
}
