package com.socialnetwork.moderation_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModerationMessageResponse {
  private String id; // ID tin nhắn (UUID)
  private Long conversationId;

  private Long senderId;
  private String senderName;
  private String senderAvatar;

  private String content;
  private String sentAt; // Timestamp trong JSON thường là String

  private List<Map<String, Object>> media;

  private Instant deletedAt;
  private Boolean isSystemBan;

  private long reportCount;
  private long complaintCount;
}
