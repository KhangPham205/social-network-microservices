package com.socialnetwork.moderation_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ModerationMessageDetail {
    private String id;
    private Long senderId;
    private String senderName;
    private String content;
    private Instant sentAt;
    private Instant deletedAt;
    private boolean isSystemBan; // Cờ báo hiệu do Admin xóa hay User tự xóa
}
