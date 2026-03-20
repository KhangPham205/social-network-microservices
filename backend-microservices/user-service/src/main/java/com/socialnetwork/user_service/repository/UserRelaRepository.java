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

  @Modifying
  @Query("DELETE FROM UserRela u WHERE u.follower = :follower AND u.following = :following")
  void deleteByFollowerAndFollowing(
      @Param("follower") User follower, @Param("following") User following);

  // [MỚI THÊM] Lấy danh sách ID những người mà TÔI (viewerId) đang follow trong tập targetIds
  @Query(
      "SELECT r.following.id FROM UserRela r WHERE r.follower.id = :viewerId AND r.following.id IN :targetIds")
  Set<Long> findFollowingIdsByViewerAndTargets(
      @Param("viewerId") Long viewerId, @Param("targetIds") List<Long> targetIds);

  // [MỚI THÊM] Lấy danh sách ID những người đang follow TÔI (viewerId) trong tập targetIds
  @Query(
      "SELECT r.follower.id FROM UserRela r WHERE r.following.id = :viewerId AND r.follower.id IN :targetIds")
  Set<Long> findFollowerIdsByViewerAndTargets(
      @Param("viewerId") Long viewerId, @Param("targetIds") List<Long> targetIds);
}
