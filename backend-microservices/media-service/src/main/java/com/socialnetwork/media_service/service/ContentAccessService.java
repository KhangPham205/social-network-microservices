package com.socialnetwork.media_service.service;

import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.media_service.model.Comment;
import com.socialnetwork.media_service.model.Post;

/**
 * Single place that decides who may see a post or a comment. Used by the post, comment and react
 * services so a reaction list can never leak content the viewer is not allowed to open.
 */
public interface ContentAccessService {

  /**
   * Throws when {@code viewerId} may not see {@code post}.
   *
   * @throws com.socialnetwork.common.exception.ResourceNotFoundException when the post is deleted
   *     or system-banned and the viewer is not its author
   * @throws com.socialnetwork.common.exception.AccessDeniedException when the visibility scope
   *     excludes the viewer
   */
  void requireViewPermission(Post post, Long viewerId);

  /** Same rules as {@link #requireViewPermission} but expressed as a predicate. */
  boolean canView(Post post, Long viewerId);

  /** Loads a post and applies {@link #requireViewPermission}. */
  Post requireVisiblePost(Long postId, Long viewerId);

  /** Loads a comment, checks it is visible and that its post is too. */
  Comment requireVisibleComment(Long commentId, Long viewerId);

  /** Checks a reaction target (POST or COMMENT) before exposing who reacted to it. */
  void requireViewPermissionOnTarget(Long targetId, TargetType targetType, Long viewerId);
}
