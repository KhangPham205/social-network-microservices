package com.socialnetwork.user_service.service;

import com.socialnetwork.user_service.model.Friendship;
import com.socialnetwork.user_service.model.User;
import com.socialnetwork.user_service.model.node.UserNode;
import com.socialnetwork.user_service.repository.FriendshipRepository;
import com.socialnetwork.user_service.repository.UserRepository;
import com.socialnetwork.user_service.repository.neo4j.UserNodeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.socialnetwork.common.vo.FriendshipStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class Neo4jMigrationService {

  private final UserRepository userRepository;
  private final FriendshipRepository friendshipRepository;
  private final UserNodeRepository userNodeRepository;

  // Chú ý: Không nên dùng @Transactional ở hàm này vì nó bao gồm cả việc
  // đọc JPA và ghi Neo4j, có thể gây kẹt connection.
  public String runMigration() {
    log.info("--- Starting Neo4j Data Migration ---");

    try {
      // 1. Migrate Users
      List<User> users = userRepository.findAll();
      log.info("Found {} users in PostgreSQL", users.size());

      List<UserNode> nodes =
          users.stream()
              .filter(user -> user != null && user.getId() != null) // Chống null User
              .map(
                  user -> {
                    UserNode node = new UserNode();
                    node.setId(user.getId());
                    // Cung cấp giá trị mặc định nếu DisplayName bị null
                    node.setDisplayName(
                        user.getDisplayName() != null ? user.getDisplayName() : "Unknown User");
                    return node;
                  })
              .toList();

      if (nodes.isEmpty()) {
        log.info("Không có User nào để đồng bộ sang Neo4j.");
      } else {
        userNodeRepository.saveAll(nodes); // Dòng 46 cũ của bạn
        log.info("Saved {} users to Neo4j", nodes.size());
      }
      log.info("Saved {} users to Neo4j", nodes.size());

      // 2. Migrate Friendships
      List<Friendship> friendships = friendshipRepository.findAllWithUsers();
      int relationshipCount = 0;

      for (Friendship f : friendships) {
        // Kiểm tra null toàn diện
        if (f == null || f.getStatus() == null) continue;

        if (f.getStatus() == FriendshipStatus.FRIEND) {
          if (f.getSender() == null || f.getReceiver() == null) {
            log.warn("Bỏ qua Friendship do Sender hoặc Receiver bị null (Bản ghi DB rác)");
            continue;
          }

          Long senderId = f.getSender().getId();
          Long receiverId = f.getReceiver().getId();

          if (senderId == null || receiverId == null) continue;

          try {
            userNodeRepository.createFriendship(senderId, receiverId);
            relationshipCount++;
          } catch (Exception e) {
            log.error("Failed to create friendship between {} and {}", senderId, receiverId, e);
          }
        }
      }

      log.info("Created {} FRIEND_WITH relationships in Neo4j", relationshipCount);
      log.info("--- Neo4j Data Migration Completed ---");

      return String.format(
          "Migration completed: %d users, %d friendships synced.", nodes.size(), relationshipCount);

    } catch (Exception e) {
      log.error("Lỗi nghiêm trọng khi chạy Migration: ", e);
      throw new RuntimeException("Lỗi Migration: " + e.getMessage());
    }
  }
}
