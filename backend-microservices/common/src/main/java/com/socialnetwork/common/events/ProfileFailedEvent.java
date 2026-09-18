package com.socialnetwork.common.events;

/** user-service -> {@code KafkaTopics.PROFILE_FAILED}. Saga failure reply, auth compensates. */
public record ProfileFailedEvent(Long accountId, String reason) {}
