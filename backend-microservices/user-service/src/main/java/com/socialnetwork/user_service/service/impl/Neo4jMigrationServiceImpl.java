package com.socialnetwork.user_service.service.impl;

import com.socialnetwork.common.vo.FriendshipStatus;
import com.socialnetwork.user_service.model.Friendship;
import com.socialnetwork.user_service.model.User;
import com.socialnetwork.user_service.model.node.UserNode;
import com.socialnetwork.user_service.repository.FriendshipRepository;
import com.socialnetwork.user_service.repository.UserRepository;
import com.socialnetwork.user_service.repository.neo4j.UserNodeRepository;
import com.socialnetwork.user_service.service.Neo4jMigrationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Deliberately not {@code @Transactional}: it reads from PostgreSQL and writes to Neo4j, and
 * holding both connections for the whole run would only starve the pools.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Neo4jMigrationServiceImpl implements Neo4jMigrationService {

  private final UserRepository userRepository;
  private final FriendshipRepository friendshipRepository;
  private final UserNodeRepository userNodeRepository;

  @Override
  public String runMigration() {
    log.info("Starting the Neo4j data migration");

    List<UserNode> nodes =
        userRepository.findAll().stream()
            .filter(user -> user.getId() != null)
            .map(this::toNode)
            .toList();
    if (!nodes.isEmpty()) {
      userNodeRepository.saveAll(nodes);
    }
    log.info("Synced {} users to Neo4j", nodes.size());

    int relationships = 0;
    for (Friendship f : friendshipRepository.findAllWithUsers()) {
      if (f.getStatus() != FriendshipStatus.FRIEND
          || f.getSender() == null
          || f.getReceiver() == null) {
        continue;
      }
      userNodeRepository.createFriendship(f.getSender().getId(), f.getReceiver().getId());
      relationships++;
    }

    log.info("Neo4j data migration finished: {} users, {} friendships", nodes.size(), relationships);
    return "Migration completed: %d users, %d friendships synced.".formatted(nodes.size(), relationships);
  }

  private UserNode toNode(User user) {
    UserNode node = new UserNode();
    node.setId(user.getId());
    node.setDisplayName(user.getDisplayName() != null ? user.getDisplayName() : "Unknown User");
    return node;
  }
}
