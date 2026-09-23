package com.socialnetwork.user_service.event;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.FriendAcceptedEvent;
import com.socialnetwork.common.events.FriendshipDeletedEvent;
import com.socialnetwork.common.events.NotificationEvent;
import com.socialnetwork.common.events.ProfileCreatedEvent;
import com.socialnetwork.common.events.ProfileFailedEvent;
import com.socialnetwork.common.events.ProfileUpdatedEvent;
import com.socialnetwork.common.vo.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Single exit point for the events this service owns. Everything is sent <em>after</em> the current
 * transaction commits, so no consumer can ever observe a state the database rolled back.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public void publishNotification(
      Long actorId, Long receiverId, NotificationType type, Long targetId, Long postId) {
    send(
        KafkaTopics.NOTIFICATION,
        receiverId.toString(),
        NotificationEvent.of(actorId, receiverId, type, targetId, postId));
  }

  public void publishFriendAccepted(Long senderId, Long receiverId) {
    send(
        KafkaTopics.FRIENDSHIP_EVENTS,
        senderId.toString(),
        new FriendAcceptedEvent(senderId, receiverId));
  }

  /** Emitted on unfriend and on block: both end the relationship for the other services. */
  public void publishFriendshipDeleted(Long user1Id, Long user2Id) {
    send(
        KafkaTopics.FRIENDSHIP_EVENTS,
        user1Id.toString(),
        new FriendshipDeletedEvent(user1Id, user2Id));
  }

  public void publishProfileCreated(Long accountId) {
    send(KafkaTopics.PROFILE_CREATED, accountId.toString(), new ProfileCreatedEvent(accountId));
  }

  public void publishProfileFailed(Long accountId, String reason) {
    send(
        KafkaTopics.PROFILE_FAILED,
        accountId.toString(),
        new ProfileFailedEvent(accountId, reason));
  }

  public void publishProfileUpdated(Long accountId, String displayName, String avatarUrl) {
    send(
        KafkaTopics.PROFILE_UPDATED,
        accountId.toString(),
        new ProfileUpdatedEvent(accountId, displayName, avatarUrl));
  }

  /** Defers the send to the commit of the running transaction, or sends now when there is none. */
  private void send(String topic, String key, Object event) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              doSend(topic, key, event);
            }
          });
      return;
    }
    doSend(topic, key, event);
  }

  private void doSend(String topic, String key, Object event) {
    kafkaTemplate.send(topic, key, event);
    log.info("Published {} to {} with key {}", event.getClass().getSimpleName(), topic, key);
  }
}
