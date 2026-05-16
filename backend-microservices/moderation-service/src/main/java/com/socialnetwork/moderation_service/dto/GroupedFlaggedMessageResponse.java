package com.socialnetwork.moderation_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class GroupedFlaggedMessageResponse {
    private Long conversationId;
    private String conversationTitle;
    private boolean isGroup;
    private Instant lastUpdatedAt; // Để biết hội thoại này mới hay cũ

    // Danh sách các tin nhắn vi phạm trong hội thoại này
    private List<ModerationMessageResponse> flaggedMessages;
}
