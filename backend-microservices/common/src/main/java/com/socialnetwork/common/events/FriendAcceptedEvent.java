package com.socialnetwork.common.events;

/** user-service -> {@code KafkaTopics.FRIENDSHIP_EVENTS}. */
public record FriendAcceptedEvent(Long senderId, Long receiverId) {}
