package com.socialnetwork.common.events;

import java.util.List;

/** chat-service -> {@code KafkaTopics.CHAT_NOTIFICATION}. One event per chat message. */
public record MessageNotificationEvent(
    String messageId,
    Long roomId,
    Long senderId,
    String senderName,
    String previewContent,
    List<Long> recipientIds) {}
