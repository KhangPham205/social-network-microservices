package com.socialnetwork.user_service.repository;

import com.socialnetwork.common.vo.FriendshipStatus;
import com.socialnetwork.user_service.model.Friendship;
import com.socialnetwork.user_service.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendshipRepository
    extends JpaRepository<Friendship, Long>, JpaSpecificationExecutor<Friendship> {

  /**
   * Redeclared only to attach the entity graph: every list endpoint reads both ends of the row, so
   * without it each page costs 2 * pageSize extra selects.
   */
  @Override
  @EntityGraph(attributePaths = {"sender", "receiver"})
  Page<Friendship> findAll(Specification<Friendship> spec, Pageable pageable);

  Optional<Friendship> findBySenderAndReceiver(User sender, User receiver);

  Optional<Friendship> findBySenderAndReceiverAndStatus(
      User sender, User receiver, FriendshipStatus status);

  boolean existsBySenderIdAndReceiverIdAndStatus(
      Long senderId, Long receiverId, FriendshipStatus status);

  /** Rows between the viewer and any of the given users, in either direction. */
  @Query(
      """
      SELECT f FROM Friendship f
      JOIN FETCH f.sender
      JOIN FETCH f.receiver
      WHERE (f.sender.id = :viewerId AND f.receiver.id IN :targetIds)
         OR (f.receiver.id = :viewerId AND f.sender.id IN :targetIds)
      """)
  List<Friendship> findFriendshipsBetween(
      @Param("viewerId") Long viewerId, @Param("targetIds") List<Long> targetIds);

  /** The FRIEND row of a pair, whoever sent the original request. */
  @Query(
      """
      SELECT f FROM Friendship f
      WHERE f.status = com.socialnetwork.common.vo.FriendshipStatus.FRIEND
        AND ((f.sender.id = :user1 AND f.receiver.id = :user2)
          OR (f.sender.id = :user2 AND f.receiver.id = :user1))
      """)
  List<Friendship> findFriendRowsBetween(@Param("user1") Long user1, @Param("user2") Long user2);

  @Query(
      """
      SELECT COUNT(f) > 0 FROM Friendship f
      WHERE f.status = com.socialnetwork.common.vo.FriendshipStatus.FRIEND
        AND ((f.sender.id = :user1 AND f.receiver.id = :user2)
          OR (f.sender.id = :user2 AND f.receiver.id = :user1))
      """)
  boolean existsActiveFriendship(@Param("user1") Long user1, @Param("user2") Long user2);

  /** Ids this user has blocked. */
  @Query(
      """
      SELECT f.receiver.id FROM Friendship f
      WHERE f.sender.id = :userId
        AND f.status = com.socialnetwork.common.vo.FriendshipStatus.BLOCKED
      """)
  List<Long> findBlockedUserIds(@Param("userId") Long userId);

  /** Ids that have blocked this user. */
  @Query(
      """
      SELECT f.sender.id FROM Friendship f
      WHERE f.receiver.id = :userId
        AND f.status = com.socialnetwork.common.vo.FriendshipStatus.BLOCKED
      """)
  List<Long> findUserIdsBlocking(@Param("userId") Long userId);

  /** True when either user blocked the other; one query instead of two round trips. */
  @Query(
      """
      SELECT COUNT(f) > 0 FROM Friendship f
      WHERE f.status = com.socialnetwork.common.vo.FriendshipStatus.BLOCKED
        AND ((f.sender.id = :user1 AND f.receiver.id = :user2)
          OR (f.sender.id = :user2 AND f.receiver.id = :user1))
      """)
  boolean existsBlockBetween(@Param("user1") Long user1, @Param("user2") Long user2);

  @Query("SELECT f FROM Friendship f JOIN FETCH f.sender JOIN FETCH f.receiver")
  List<Friendship> findAllWithUsers();
}
