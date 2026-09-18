package com.socialnetwork.common.events;

import com.socialnetwork.common.vo.AccountStatus;

/** moderation-service -> {@code KafkaTopics.USER_MODERATION_ACTIONS}; consumed by auth-service. */
public record UserModerationEvent(Long userId, AccountStatus newStatus, String reason) {}
