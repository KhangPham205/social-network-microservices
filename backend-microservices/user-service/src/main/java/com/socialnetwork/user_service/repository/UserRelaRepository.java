package com.socialnetwork.user_service.repository;

import com.socialnetwork.user_service.model.User;
import com.socialnetwork.user_service.model.UserRela;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRelaRepository extends JpaRepository<UserRela, Long> {

  boolean existsByFollowerAndFollowing(User follower, User following);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("DELETE FROM UserRela u WHERE u.follower = :follower AND u.following = :following")
  void deleteByFollowerAndFollowing(
      @Param("follower") User follower, @Param("following") User following);

  /** Drops both directions of a follow in one statement (unfriend, block). */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      DELETE FROM UserRela u
      WHERE (u.follower.id = :user1 AND u.following.id = :user2)
         OR (u.follower.id = :user2 AND u.following.id = :user1)
      """)
  void deleteFollowsBetween(@Param("user1") Long user1, @Param("user2") Long user2);

  /** Follow edges where this user is the follower. */
  List<UserRela> findByFollower(User follower);

  /** Of {@code targetIds}, the ones the viewer follows. */
  @Query(
      "SELECT r.following.id FROM UserRela r WHERE r.follower.id = :viewerId AND r.following.id IN :targetIds")
  Set<Long> findFollowingIdsByViewerAndTargets(
      @Param("viewerId") Long viewerId, @Param("targetIds") List<Long> targetIds);

  /** Of {@code targetIds}, the ones that follow the viewer. */
  @Query(
      "SELECT r.follower.id FROM UserRela r WHERE r.following.id = :viewerId AND r.follower.id IN :targetIds")
  Set<Long> findFollowerIdsByViewerAndTargets(
      @Param("viewerId") Long viewerId, @Param("targetIds") List<Long> targetIds);
}
