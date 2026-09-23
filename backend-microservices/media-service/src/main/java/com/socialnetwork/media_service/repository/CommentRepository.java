package com.socialnetwork.media_service.repository;

import com.socialnetwork.media_service.model.Comment;
import com.socialnetwork.media_service.model.Post;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Comments are soft deleted; reads filter {@code deletedAt} out. */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

  Optional<Comment> findByIdAndDeletedAtIsNull(Long id);

  List<Comment> findByIdInAndDeletedAtIsNull(List<Long> ids);

  Page<Comment> findByPostAndParentIsNullAndDeletedAtIsNull(Post post, Pageable pageable);

  Page<Comment> findByParentAndDeletedAtIsNull(Comment parent, Pageable pageable);

  boolean existsByIdAndDeletedAtIsNull(Long id);

  @Query(
      """
      SELECT c.parent.id, COUNT(c.id)
      FROM Comment c
      WHERE c.parent.id IN :parentIds AND c.deletedAt IS NULL
      GROUP BY c.parent.id
      """)
  List<Object[]> findChildrenCountsRaw(@Param("parentIds") List<Long> parentIds);

  /** {@code parentId -> number of visible replies}. */
  default Map<Long, Integer> findChildrenCounts(List<Long> parentIds) {
    if (parentIds == null || parentIds.isEmpty()) {
      return Map.of();
    }
    return findChildrenCountsRaw(parentIds).stream()
        .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
  }
}
