package com.socialnetwork.moderation_service.dto;

import com.socialnetwork.moderation_service.enums.ReportReason;
import com.socialnetwork.moderation_service.enums.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vo.TargetType;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReportResponse {
    private Long id;
    private TargetType targetType;
    private String targetId;

    private Long reporterId;
    private String reporterName;
    private String reporterAvatar;

    private ReportReason reason;
    private String customReason;
    private ReportStatus status;
    private Boolean isBannedBySystem;
    private Instant createdAt;
}