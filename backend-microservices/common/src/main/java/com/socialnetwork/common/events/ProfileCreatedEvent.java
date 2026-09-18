package com.socialnetwork.common.events;

/** user-service -> {@code KafkaTopics.PROFILE_CREATED}. Saga success reply. */
public record ProfileCreatedEvent(Long accountId) {}
