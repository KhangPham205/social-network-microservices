package com.socialnetwork.common.events;

/** user-service -> {@code KafkaTopics.PROFILE_UPDATED}. Lets read-model caches refresh. */
public record ProfileUpdatedEvent(Long accountId, String displayName, String avatarUrl) {}
