package com.socialnetwork.moderation_service.service;

import com.socialnetwork.common.events.ContentCreatedEvent;
import com.socialnetwork.common.events.MessageCreatedEvent;
import com.socialnetwork.common.vo.AccountStatus;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.dto.ModerationLogResponse;
import com.socialnetwork.moderation_service.dto.ModerationMessageResponse;
import com.socialnetwork.moderation_service.dto.ModerationUserDetailResponse;
import com.socialnetwork.moderation_service.dto.ReportResponse;
import com.socialnetwork.moderation_service.dto.UserModerationResponse;
import com.socialnetwork.moderation_service.dto.external.CommentResponse;
import com.socialnetwork.moderation_service.dto.external.PostResponse;
import org.springframework.data.domain.Pageable;

/** Admin composition views, moderation actions and the AI moderation pipeline. */
public interface ModerationService {

  // ── Admin composition API ──────────────────────────────────────────────

  ModerationUserDetailResponse getUserDetailForAdmin(Long userId);

  PageVO<ReportResponse> getUserViolations(Long userId, Pageable pageable);

  ModerationMessageResponse getMessageDetailForAdmin(String messageId);

  PageVO<UserModerationResponse> getUsersWithReportCount(Pageable pageable, String filter);

  PageVO<PostResponse> getFlaggedPosts(String filter, Pageable pageable);

  PostResponse getPostDetailForAdmin(Long postId);

  PageVO<CommentResponse> getFlaggedComments(String filter, Pageable pageable);

  CommentResponse getCommentDetailForAdmin(Long commentId);

  PageVO<ModerationMessageResponse> getFlaggedMessages(String filter, Pageable pageable);

  PageVO<ModerationLogResponse> getModerationLogs(String filter, Pageable pageable);

  PageVO<ModerationLogResponse> getHistory(
      TargetType type, String id, Pageable pageable, String filter);

  // ── Moderation actions ─────────────────────────────────────────────────

  /** Blocks or unblocks an account; the new status is sent to auth-service. */
  void updateUserStatus(Long userId, AccountStatus newStatus, String reason);

  /**
   * Hides a post, comment or message.
   *
   * @param targetId numeric id for POST/COMMENT, Mongo ObjectId for MESSAGE
   * @param reportId report this action resolves, or {@code null}
   */
  void blockContent(String targetId, TargetType targetType, Long reportId);

  /**
   * Restores a post, comment or message.
   *
   * @param targetId numeric id for POST/COMMENT, Mongo ObjectId for MESSAGE
   * @param complaintId complaint this action resolves, or {@code null}
   */
  void unblockContent(String targetId, TargetType targetType, Long complaintId);

  // ── AI moderation pipeline (Kafka driven) ──────────────────────────────

  /** Scans a new post or comment; a failing scan throws so Kafka retry/DLT takes over. */
  void moderateContent(ContentCreatedEvent event);

  /** Scans a new chat message; a failing scan throws so Kafka retry/DLT takes over. */
  void moderateMessage(MessageCreatedEvent event);
}
