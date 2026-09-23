package com.socialnetwork.media_service.service.impl;

import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.security.SecurityUtils;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.media_service.client.UserServiceClient;
import com.socialnetwork.media_service.enums.AccessScope;
import com.socialnetwork.media_service.model.Comment;
import com.socialnetwork.media_service.model.Post;
import com.socialnetwork.media_service.repository.CommentRepository;
import com.socialnetwork.media_service.repository.PostRepository;
import com.socialnetwork.media_service.service.ContentAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentAccessServiceImpl implements ContentAccessService {

  private static final String ADMIN = "ADMIN";

  private final PostRepository postRepository;
  private final CommentRepository commentRepository;
  private final UserServiceClient userServiceClient;

  @Override
  public void requireViewPermission(Post post, Long viewerId) {
    Long authorId = post.getAuthor().getId();
    if (authorId.equals(viewerId) || SecurityUtils.hasRole(ADMIN)) {
      return;
    }
    // Removed or system-banned content simply does not exist for anybody but its author.
    if (post.getDeletedAt() != null || Boolean.TRUE.equals(post.getIsSystemBan())) {
      throw new ResourceNotFoundException("Post not found with id: " + post.getId());
    }
    if (post.getAccessModifier() == AccessScope.PRIVATE) {
      throw new AccessDeniedException("This post is private.");
    }
    if (post.getAccessModifier() == AccessScope.FRIENDS
        && !userServiceClient.isFriend(authorId, viewerId)) {
      throw new AccessDeniedException("Only friends can view this post.");
    }
  }

  @Override
  public boolean canView(Post post, Long viewerId) {
    try {
      requireViewPermission(post, viewerId);
      return true;
    } catch (AccessDeniedException | ResourceNotFoundException e) {
      return false;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Post requireVisiblePost(Long postId, Long viewerId) {
    Post post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId));
    requireViewPermission(post, viewerId);
    return post;
  }

  @Override
  @Transactional(readOnly = true)
  public Comment requireVisibleComment(Long commentId, Long viewerId) {
    Comment comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Comment not found with id: " + commentId));
    Long authorId = comment.getAuthor().getId();
    boolean privileged = authorId.equals(viewerId) || SecurityUtils.hasRole(ADMIN);
    if (!privileged
        && (comment.getDeletedAt() != null || Boolean.TRUE.equals(comment.getIsSystemBan()))) {
      throw new ResourceNotFoundException("Comment not found with id: " + commentId);
    }
    requireViewPermission(comment.getPost(), viewerId);
    return comment;
  }

  @Override
  public void requireViewPermissionOnTarget(Long targetId, TargetType targetType, Long viewerId) {
    if (targetType == null) {
      throw new BadRequestException("targetType is required");
    }
    switch (targetType) {
      case POST -> requireVisiblePost(targetId, viewerId);
      case COMMENT -> requireVisibleComment(targetId, viewerId);
      default -> throw new BadRequestException("Unsupported target type: " + targetType);
    }
  }
}
