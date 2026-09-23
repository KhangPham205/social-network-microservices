package com.socialnetwork.moderation_service.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/** Admin view of a chat message, composed from chat-service and the local report counters. */
@Data
@Builder
public class ModerationMessageResponse {
  /** Mongo ObjectId of the message. */
  private String id;

  private Long conversationId;

  private Long senderId;
  private String senderName;
  private String senderAvatar;

  private String content;
  private Instant sentAt;

  private Boolean isSystemBan;

  private long reportCount;
  private long complaintCount;
}
