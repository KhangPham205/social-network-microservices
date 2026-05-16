package com.socialnetwork.moderation_service.dto;

import com.kt.social.auth.enums.AccountStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ModerationUserDetailResponse {
    private Long id;
    private String displayName;
    private String avatarUrl;
    private String email;           // Thông tin riêng tư
    private String bio;
    private AccountStatus status;   // ACTIVE/BLOCKED
    private Long violationCount;    // Số lần bị báo cáo thành công (Approved Reports)
    private Instant createdAt;
    private Instant lastActiveAt;
}