package com.socialnetwork.user_service.handler;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.FriendAcceptedEvent;
import com.socialnetwork.common.events.FriendshipDeletedEvent;
import com.socialnetwork.user_service.repository.neo4j.UserNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the {@code FRIENDS_WITH} edges in sync with the relational rows. The topic carries two
 * event types, so the payload type selects the handler.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@KafkaListener(topics = KafkaTopics.FRIENDSHIP_EVENTS, groupId = Neo4jSyncHandler.NEO4J_GROUP)
public class Neo4jFriendshipSyncHandler {

  private final UserNodeRepository userNodeRepository;

  @KafkaHandler
  public void handleFriendAccepted(FriendAcceptedEvent event) {
    log.info("Adding FRIENDS_WITH between {} and {}", event.senderId(), event.receiverId());
    userNodeRepository.createFriendship(event.senderId(), event.receiverId());
  }

  @KafkaHandler
  public void handleFriendshipDeleted(FriendshipDeletedEvent event) {
    log.info("Deleting FRIENDS_WITH between {} and {}", event.user1Id(), event.user2Id());
    userNodeRepository.deleteFriendship(event.user1Id(), event.user2Id());
  }

  @KafkaHandler(isDefault = true)
  public void handleUnknown(Object payload) {
    log.warn(
        "Ignoring unknown payload {} on topic {}",
        payload == null ? "null" : payload.getClass().getName(),
        KafkaTopics.FRIENDSHIP_EVENTS);
  }
}
