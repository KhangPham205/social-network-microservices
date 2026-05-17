package com.socialnetwork.moderation_service.service;

import com.socialnetwork.moderation_service.dto.*;
import com.socialnetwork.moderation_service.dto.external.CommentResponse;
import com.socialnetwork.moderation_service.dto.external.PostExternalDto;
import com.socialnetwork.moderation_service.dto.external.PostResponse;
import com.socialnetwork.moderation_service.enums.AccountStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import vo.PageVO;
import vo.TargetType;

public interface ModerationService {

  ModerationUserDetailResponse getUserDetailForAdmin(Long userId);

  PageVO<ReportResponse> getUserViolations(Long userId, Pageable pageable);

  ModerationMessageResponse getMessageDetailForAdmin(String messageId);

  PageVO<UserModerationResponse> getUsersWithReportCount(Pageable pageable, String filter);

  PageVO<PostResponse> getFlaggedPosts(String filter, Pageable pageable);

  PageVO<CommentResponse> getFlaggedComments(String filter, Pageable pageable);

  PageVO<ModerationMessageResponse> getFlaggedMessages(String filter, Pageable pageable);

  PageVO<GroupedFlaggedMessageResponse> getGroupedFlaggedMessages(Pageable pageable);

  @Transactional(readOnly = true)
  PageVO<ModerationLogResponse> getModerationLogs(String filter, Pageable pageable);

  PageVO<ModerationLogResponse> getHistory(
      TargetType type, String id, Pageable pageable, String filter);

  void updateUserStatus(Long userId, AccountStatus newStatus, String reason);

  void blockContent(String id, TargetType type);

  void unblockContent(Long id, TargetType type);

  void validatePostContent(PostExternalDto post);

  void validateImage(Long mediaId, byte[] imageBytes, String filename);

  void validateTextContent(Long targetId, TargetType targetType, String content);

  void validateMediaContent(Long targetId, TargetType targetType, String url);
}
