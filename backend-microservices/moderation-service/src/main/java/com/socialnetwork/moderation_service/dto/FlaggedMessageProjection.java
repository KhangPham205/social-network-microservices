package com.socialnetwork.moderation_service.dto;

import java.time.Instant;

public interface FlaggedMessageProjection {
    String getId();
    Long getConversationId();
    String getConversationTitle();
    Boolean getIsGroup();
    Long getSenderId();
    String getSenderName();
    String getSenderAvatar();
    String getContent();
    String getSentAt(); // Hoặc Instant tùy bạn cấu hình
    Instant getDeletedAt();

    String getMedia();
}
