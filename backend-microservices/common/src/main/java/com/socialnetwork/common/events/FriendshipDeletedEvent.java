package com.socialnetwork.common.events;

/** user-service -> {@code KafkaTopics.FRIENDSHIP_EVENTS}. Emitted on unfriend and block. */
public record FriendshipDeletedEvent(Long user1Id, Long user2Id) {}
