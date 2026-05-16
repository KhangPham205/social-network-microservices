package com.socialnetwork.moderation_service.dto;

import com.socialnetwork.moderation_service.enums.ReportReason;
import lombok.Data;
import vo.TargetType;

@Data
public class CreateReportRequest {
    private TargetType targetType; // POST, COMMENT, USER, MESSAGE
    private String targetId;
    private ReportReason reason;
    private String customReason;   // If reason is OTHER
}