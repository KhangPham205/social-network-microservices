package com.socialnetwork.common.dto;

import java.time.Instant;

/** What chat-service exposes to moderation-service about a message (internal API). */
public record MessageModerationView(
    String id, Long roomId, Long senderId, String content, Instant createdAt, boolean systemBanned) {}
