package com.socialnetwork.media_service.repository;

import com.socialnetwork.media_service.model.Post;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

  @Query("SELECT COUNT(p) FROM Post p WHERE p.sharedPost.id = :postId")
  int countSharesByPostId(@Param("postId") Long postId);

  @Modifying
  @Transactional
  @Query("UPDATE Post p SET p.commentCount = p.commentCount + :delta WHERE p.id = :postId")
  void updateCommentCount(@Param("postId") Long postId, @Param("delta") int delta);

  @Query(
      """
    SELECT p.sharedPost.id, COUNT(p.id)
    FROM Post p
    WHERE p.sharedPost.id IN :postIds
    GROUP BY p.sharedPost.id
""")
  List<Object[]> findShareCountsRaw(@Param("postIds") List<Long> postIds);

  // Hàm default để chuyển List<Object[]> thành Map
  default Map<Long, Integer> findShareCounts(List<Long> postIds) {
    if (postIds == null || postIds.isEmpty()) {
      return Map.of();
    }
    return findShareCountsRaw(postIds).stream()
        .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
  }

  @Query(value = "SELECT * FROM posts WHERE id = :id", nativeQuery = true)
  Optional<Post> findByIdIncludingDeleted(@Param("id") Long id);

  @Query(
      value =
          """
        SELECT DISTINCT p.* FROM posts p
        LEFT JOIN reports r ON (r.target_id = CAST(p.id AS VARCHAR) AND r.target_type = 'POST')
        WHERE (p.deleted_at IS NOT NULL OR p.is_system_ban = true OR r.id IS NOT NULL)
        AND (:filter IS NULL OR p.content ILIKE %:filter%)
        """,
      countQuery =
          """
        SELECT count(DISTINCT p.id) FROM posts p
        LEFT JOIN reports r ON (r.target_id = CAST(p.id AS VARCHAR) AND r.target_type = 'POST')
        WHERE (p.deleted_at IS NOT NULL OR p.system_ban = true OR r.id IS NOT NULL)
        AND (:filter IS NULL OR p.content ILIKE %:filter%)
        """,
      nativeQuery = true)
  Page<Post> findAllFlaggedPosts(@Param("filter") String filter, Pageable pageable);

  @Query(
      value =
          """
        SELECT CAST(EXTRACT(MONTH FROM created_at) AS INTEGER) as time_unit, COUNT(*)
        FROM users
        WHERE EXTRACT(YEAR FROM created_at) = :year
        GROUP BY time_unit
    """,
      nativeQuery = true)
  List<Object[]> countByYear(@Param("year") int year);

  @Query(
      value =
          """
        SELECT CAST(EXTRACT(DAY FROM created_at) AS INTEGER) as time_unit, COUNT(*)
        FROM users
        WHERE EXTRACT(MONTH FROM created_at) = :month
          AND EXTRACT(YEAR FROM created_at) = :year
        GROUP BY time_unit
    """,
      nativeQuery = true)
  List<Object[]> countByMonth(@Param("month") int month, @Param("year") int year);
}
