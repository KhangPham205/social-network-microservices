package com.socialnetwork.user_service.handler;

import com.socialnetwork.user_service.events.FriendshipAcceptedEvent;
import com.socialnetwork.user_service.events.FriendshipDeletedEvent;
import com.socialnetwork.user_service.model.node.UserNode;
import com.socialnetwork.user_service.repository.neo4j.UserNodeRepository;
import events.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class Neo4jSyncHandler {

  private final UserNodeRepository userNodeRepository;

  @KafkaListener(topics = "user-created-topic", groupId = "neo4j-sync-group")
  public void handleUserCreatedEvent(UserCreatedEvent event) {
    log.info("Neo4j Sync: Creating UserNode for user {}", event.accountId());
    try {
      if (!userNodeRepository.existsById(event.accountId())) {
        UserNode node = new UserNode();
        node.setId(event.accountId());
        node.setDisplayName(event.username());
        userNodeRepository.save(node);
      }
    } catch (Exception e) {
      log.error("Failed to sync new user to Neo4j", e);
    }
  }

  @KafkaListener(topics = "friendship-events", groupId = "neo4j-sync-group")
  public void handleFriendshipEvents(Object rawEvent) {
    // Because of the spring.json.type.mapping in application.yaml,
    // the deserializer will output the appropriate Event class.
    try {
      if (rawEvent instanceof FriendshipAcceptedEvent event) {
        log.info(
            "Neo4j Sync: Adding friendship between {} and {}",
            event.senderId(),
            event.receiverId());
        userNodeRepository.createFriendship(event.senderId(), event.receiverId());
      } else if (rawEvent instanceof FriendshipDeletedEvent event) {
        log.info(
            "Neo4j Sync: Deleting friendship between {} and {}", event.user1Id(), event.user2Id());
        userNodeRepository.deleteFriendship(event.user1Id(), event.user2Id());
      }
    } catch (Exception e) {
      log.error("Failed to sync friendship event to Neo4j", e);
    }
  }
}
