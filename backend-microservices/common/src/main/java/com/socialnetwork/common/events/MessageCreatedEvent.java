package com.socialnetwork.common.events;

/** chat-service -> {@code KafkaTopics.MESSAGE_CREATED}. Text of a message for AI moderation. */
public record MessageCreatedEvent(String messageId, Long roomId, Long senderId, String content) {}
