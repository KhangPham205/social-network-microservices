package com.socialnetwork.common.events;

import com.socialnetwork.common.vo.NotificationType;

/**
 * any service -> {@code KafkaTopics.NOTIFICATION}.
 *
 * @param eventId unique id used by the consumer for idempotency (UUID)
 * @param targetId id of the object the action was performed on (comment id, post id, ...)
 * @param postId post the target belongs to, used to build the deep link; may equal targetId
 */
public record NotificationEvent(
    String eventId,
    Long actorId,
    Long receiverId,
    NotificationType type,
    Long targetId,
    Long postId) {

  public static NotificationEvent of(
      Long actorId, Long receiverId, NotificationType type, Long targetId, Long postId) {
    return new NotificationEvent(
        java.util.UUID.randomUUID().toString(), actorId, receiverId, type, targetId, postId);
  }
}
