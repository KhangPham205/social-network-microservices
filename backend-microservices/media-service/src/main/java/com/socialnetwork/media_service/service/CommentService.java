package com.socialnetwork.media_service.service;

import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.media_service.dto.comment.CommentRequest;
import com.socialnetwork.media_service.dto.comment.CommentResponse;
import com.socialnetwork.media_service.dto.comment.UpdateCommentRequest;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface CommentService {

  CommentResponse createComment(CommentRequest request);

  CommentResponse updateComment(UpdateCommentRequest request);

  /** Applied by the moderation listener; the row stays, it is only hidden. */
  void updateSystemBanStatus(Long commentId, boolean isBanned);

  PageVO<CommentResponse> getCommentsByPost(Long postId, Pageable pageable);

  PageVO<CommentResponse> getReplies(Long parentId, Pageable pageable);

  /** Soft delete: replies keep pointing at this row. */
  void deleteComment(Long id);

  /** Internal API: owner of a comment, including removed ones, for moderation. */
  Long getCommentOwnerId(Long commentId);

  /** Internal API: batch lookup used by moderation-service. */
  List<CommentResponse> getCommentsByIds(List<Long> ids);
}
