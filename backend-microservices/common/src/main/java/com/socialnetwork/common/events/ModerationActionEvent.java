package com.socialnetwork.common.events;

import com.socialnetwork.common.vo.ModerationAction;
import com.socialnetwork.common.vo.TargetType;
import lombok.Builder;

/**
 * moderation-service -> {@code KafkaTopics.MODERATION_ACTIONS}. Tells the owning service to hide
 * or restore a piece of content.
 *
 * @param targetId numeric id for POST/COMMENT, Mongo ObjectId string for MESSAGE
 * @param reason human readable reason, may be null
 */
@Builder
public record ModerationActionEvent(
    String targetId, TargetType targetType, ModerationAction action, String reason) {}
