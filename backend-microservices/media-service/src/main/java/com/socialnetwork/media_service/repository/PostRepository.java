package com.socialnetwork.media_service.repository;

import com.socialnetwork.media_service.model.Post;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Posts are soft deleted, so every read path must exclude {@code deletedAt is not null} rows. The
 * plain {@code findById} is reserved for moderation and ownership lookups that also need to see
 * removed content.
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

  Optional<Post> findByIdAndDeletedAtIsNull(Long id);

  List<Post> findByIdInAndDeletedAtIsNull(List<Long> ids);

  @Modifying
  @Query("UPDATE Post p SET p.commentCount = p.commentCount + :delta WHERE p.id = :postId")
  void updateCommentCount(@Param("postId") Long postId, @Param("delta") int delta);

  @Modifying
  @Query("UPDATE Post p SET p.shareCount = p.shareCount + :delta WHERE p.id = :postId")
  void updateShareCount(@Param("postId") Long postId, @Param("delta") int delta);
}
