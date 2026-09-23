package com.socialnetwork.user_service.handler;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.UserCreatedEvent;
import com.socialnetwork.user_service.model.node.UserNode;
import com.socialnetwork.user_service.repository.neo4j.UserNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Mirrors new accounts into the recommendation graph. Runs in its own consumer group so a Neo4j
 * outage never blocks the registration saga.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Neo4jSyncHandler {

  /** Separate group: the saga listener reads the same topic as {@code user-service}. */
  public static final String NEO4J_GROUP = "user-service-neo4j";

  private final UserNodeRepository userNodeRepository;

  @KafkaListener(topics = KafkaTopics.USER_CREATED, groupId = NEO4J_GROUP)
  public void handleUserCreatedEvent(UserCreatedEvent event) {
    if (userNodeRepository.existsById(event.accountId())) {
      log.debug("UserNode {} already exists, skipping", event.accountId());
      return;
    }
    UserNode node = new UserNode();
    node.setId(event.accountId());
    node.setDisplayName(event.username());
    userNodeRepository.save(node);
    log.info("Created UserNode for user {}", event.accountId());
  }
}
