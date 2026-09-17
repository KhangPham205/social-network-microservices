package com.socialnetwork.user_service.repository;

import com.socialnetwork.user_service.model.Friendship;
import com.socialnetwork.user_service.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vo.friendship.FriendshipStatus;

@Repository
public interface FriendshipRepository
    extends JpaRepository<Friendship, Long>, JpaSpecificationExecutor<Friendship> {

  Optional<Friendship> findBySenderAndReceiver(User sender, User receiver);

  @Query(
      """
    SELECT f FROM Friendship f
    WHERE
        (f.sender.id = :viewerId AND f.receiver.id IN :targetIds)
        OR
        (f.receiver.id = :viewerId AND f.sender.id IN :targetIds)
""")
  List<Friendship> findFriendshipsBetween(
      @Param("viewerId") Long viewerId, @Param("targetIds") List<Long> targetIds);

  @Query(
      "SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f WHERE ((f.sender = :user1 AND f.receiver = :user2) OR (f.sender = :user2 AND f.receiver = :user1)) AND f.status = 'FRIEND'")
  boolean existsActiveFriendship(@Param("user1") User user1, @Param("user2") User user2);

  boolean existsBySenderIdAndReceiverIdAndStatus(
      Long senderId, Long receiverId, FriendshipStatus status);

  @Query(
      """
    SELECT CASE
        WHEN f.sender.id = :userId THEN f.receiver.id
        ELSE f.sender.id
    END
    FROM Friendship f
    WHERE (f.sender.id = :userId)
      AND f.status = 'BLOCKED'
""")
  int findBlockedUserIds(Long userId);

  // Trả về true/false trực tiếp từ DB chỉ với 1 câu query
  @Query(
      "SELECT COUNT(f) > 0 FROM Friendship f WHERE "
          + "((f.sender.id = :user1 AND f.receiver.id = :user2) OR "
          + "(f.sender.id = :user2 AND f.receiver.id = :user1)) "
          + "AND f.status = 'FRIEND'")
  boolean existsActiveFriendship(@Param("user1") Long user1, @Param("user2") Long user2);

  @Query("SELECT f FROM Friendship f JOIN FETCH f.sender JOIN FETCH f.receiver")
  List<Friendship> findAllWithUsers();
}
