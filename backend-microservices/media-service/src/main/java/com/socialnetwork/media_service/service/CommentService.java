package com.socialnetwork.media_service.service;

import com.socialnetwork.media_service.dto.comment.CommentRequest;
import com.socialnetwork.media_service.dto.comment.CommentResponse;
import com.socialnetwork.media_service.dto.comment.UpdateCommentRequest;
import org.springframework.data.domain.Pageable;
import vo.PageVO;

import java.util.List;

public interface CommentService {
  CommentResponse createComment(CommentRequest request);

  CommentResponse updateComment(UpdateCommentRequest request);

  void updateSystemBanStatus(Long commentId, boolean isBanned);

  CommentResponse getCommentById(Long id);

  PageVO<CommentResponse> getCommentsByPost(Long postId, Pageable pageable);

  PageVO<CommentResponse> getReplies(Long parentId, Pageable pageable);

  void deleteComment(Long id);

  Long getCommentOwnerId(Long commentId);
  List<CommentResponse> getCommentsByIds(List<Long> ids);
}
