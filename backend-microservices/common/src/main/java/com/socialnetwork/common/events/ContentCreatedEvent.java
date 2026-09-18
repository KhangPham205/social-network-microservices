package com.socialnetwork.common.events;

import com.socialnetwork.common.vo.TargetType;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * media-service -> {@code KafkaTopics.CONTENT_CREATED}. Consumed by moderation-service (AI check)
 * and by media-service itself (recommendation indexing).
 *
 * @param media list of {@code {type: image|video, url: ...}} entries, may be empty
 */
@Builder
public record ContentCreatedEvent(
    Long targetId,
    TargetType targetType,
    String content,
    Long authorId,
    List<Map<String, String>> media) {}
