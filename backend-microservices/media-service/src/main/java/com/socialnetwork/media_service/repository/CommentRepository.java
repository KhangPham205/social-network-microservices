package com.socialnetwork.media_service.repository;

import com.socialnetwork.media_service.model.Comment;
import com.socialnetwork.media_service.model.Post;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
  Page<Comment> findByPostAndParentIsNull(Post post, Pageable pageable);

  Page<Comment> findByParent(Comment parent, Pageable pageable);

  int countByParent(Comment parent);

  @Query(
      "SELECT c.parent.id, COUNT(c.id) FROM Comment c WHERE c.parent.id IN :parentIds GROUP BY c.parent.id")
  List<Object[]> findChildrenCountsRaw(@Param("parentIds") List<Long> parentIds);

  // Helper để chuyển đổi
  default Map<Long, Integer> findChildrenCounts(List<Long> parentIds) {
    if (parentIds == null || parentIds.isEmpty()) {
      return Map.of();
    }
    return findChildrenCountsRaw(parentIds).stream()
        .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
  }

  @Query(value = "SELECT * FROM comment WHERE id = :id", nativeQuery = true)
  Optional<Comment> findByIdIncludingDeleted(@Param("id") Long id);

  @Query("SELECT c FROM Comment c WHERE c.deletedAt IS NOT NULL")
  Page<Comment> findDeletedComments(Pageable pageable);

  @Query("SELECT c FROM Comment c WHERE c.deletedAt IS NOT NULL AND c.content LIKE %:filter%")
  Page<Comment> findDeletedCommentsWithFilter(@Param("filter") String filter, Pageable pageable);

  @Query(
      value =
          """
        SELECT DISTINCT c.* FROM comment c
        LEFT JOIN reports r ON (r.target_id = CAST(c.id AS VARCHAR) AND r.target_type = 'COMMENT')
        WHERE (c.deleted_at IS NOT NULL OR r.id IS NOT NULL)
        AND (:filter IS NULL OR c.content ILIKE %:filter%)
        """,
      countQuery =
          """
        SELECT count(DISTINCT c.id) FROM comment c
        LEFT JOIN reports r ON (r.target_id = CAST(c.id AS VARCHAR) AND r.target_type = 'COMMENT')
        WHERE (c.deleted_at IS NOT NULL OR r.id IS NOT NULL)
        AND (:filter IS NULL OR c.content ILIKE %:filter%)
        """,
      nativeQuery = true)
  Page<Comment> findAllFlaggedComments(@Param("filter") String filter, Pageable pageable);

  Page<Comment> findAll(Specification<Comment> spec, Pageable pageable);

  Integer countByParentIn(List<Comment> parents);
}
