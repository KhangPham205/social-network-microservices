package com.socialnetwork.moderation_service.service;

import com.socialnetwork.moderation_service.dto.*;
import com.socialnetwork.moderation_service.model.ModerationLog;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import vo.PageVO;
import vo.TargetType;

import java.util.Map;

public interface ModerationService {
    ModerationUserDetailResponse getUserDetailForAdmin(Long userId);
    PageVO<ReportResponse> getUserViolations(Long userId, Pageable pageable);
    ModerationMessageResponse getMessageDetailForAdmin(String messageId);
    PageVO<UserModerationResponse> getUsersWithReportCount(Pageable pageable, String filter);
    PageVO<Map<String, Object>> getFlaggedPosts(String filter, Pageable pageable);
    PageVO<Map<String, Object>> getFlaggedComments(String filter, Pageable pageable);
    PageVO<ModerationMessageResponse> getFlaggedMessages(String filter, Pageable pageable);
    PageVO<GroupedFlaggedMessageResponse> getGroupedFlaggedMessages(Pageable pageable);
    @Transactional(readOnly = true)
    PageVO<ModerationLogResponse> getModerationLogs(String filter, Pageable pageable);
    PageVO<ModerationLogResponse> getHistory(TargetType type, String id, Pageable pageable, String filter);

    void updateUserStatus(Long userId, String newStatus, String reason);

    void blockContent(String id, TargetType type);
    void unblockContent(Long id, TargetType type);

    void validatePostContent(Long postId);
    void validateImage(Long mediaId, byte[] imageBytes, String filename);
}
