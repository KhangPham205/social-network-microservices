package com.socialnetwork.moderation_service.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

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
