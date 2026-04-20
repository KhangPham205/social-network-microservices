package com.socialnetwork.chat_service.repository;

import com.socialnetwork.chat_service.model.ChatRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
  @Query(
      "SELECT c FROM ChatRoom c "
          + "JOIN c.members m1 "
          + "JOIN c.members m2 "
          + "WHERE c.isGroup = false "
          + "AND m1.id.userId = :userId1 "
          + "AND m2.id.userId = :userId2")
  Optional<ChatRoom> findExistingPrivateRoom(
      @Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
