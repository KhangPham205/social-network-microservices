package com.socialnetwork.common.events;

/** auth-service -> {@code KafkaTopics.USER_CREATED}. Starts the registration saga. */
public record UserCreatedEvent(Long accountId, String username, String email) {}
