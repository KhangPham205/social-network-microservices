package com.socialnetwork.user_service.event;

import com.socialnetwork.user_service.events.FriendshipAcceptedEvent;
import com.socialnetwork.user_service.events.FriendshipDeletedEvent;
import events.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public static final String FRIENDSHIP_EVENTS_TOPIC = "friendship-events";

  public static final String NOTIFICATION_TOPIC = "notification-topic";

  public void publishNotificationEvent(
      Long actorId, Long receiverId, Long postId, Long targetId, String type) {
    try {
      NotificationEvent event = new NotificationEvent(actorId, receiverId, type, targetId, postId);
      kafkaTemplate.send(NOTIFICATION_TOPIC, receiverId.toString(), event);
      log.info(
          "Published NotificationEvent: type={}, actor={}, receiver={}", type, actorId, receiverId);
    } catch (Exception e) {
      log.error("Error publishing NotificationEvent", e);
    }
  }

  /** Publish FriendAcceptedEvent qua Kafka để các microservices khác có thể lắng nghe */
  public void publishFriendshipAcceptedEvent(Long senderId, Long receiverId) {
    try {
      FriendshipAcceptedEvent event = new FriendshipAcceptedEvent(senderId, receiverId);
      kafkaTemplate.send(FRIENDSHIP_EVENTS_TOPIC, event.senderId().toString(), event);
      log.info(
          "Published FriendAcceptedEvent to Kafka: senderId={}, receiverId={}",
          senderId,
          receiverId);
    } catch (Exception e) {
      log.error(
          "Error publishing FriendAcceptedEvent to Kafka: senderId={}, receiverId={}, error={}",
          senderId,
          receiverId,
          e.getMessage(),
          e);
    }
  }

  public void publishFriendshipDeletedEvent(Long senderId, Long receiverId) {
    try {
      FriendshipDeletedEvent event = new FriendshipDeletedEvent(senderId, receiverId);
      kafkaTemplate.send(FRIENDSHIP_EVENTS_TOPIC, event.user1Id().toString(), event);
      log.info(
          "Published FriendshipDeletedEvent to Kafka: senderId={}, receiverId={}",
          senderId,
          receiverId);
    } catch (Exception e) {
      log.error(
          "Error publishing FriendshipDeletedEvent to Kafka: senderId={}, receiverId={}, error={}",
          senderId,
          receiverId,
          e.getMessage(),
          e);
    }
  }
}
