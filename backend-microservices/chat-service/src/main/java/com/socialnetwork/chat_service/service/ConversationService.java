package com.socialnetwork.chat_service.service;

import com.socialnetwork.chat_service.dto.*;
import com.socialnetwork.chat_service.enums.ChatLabel;
import java.util.List;

public interface ConversationService {
  ConversationResponse createConversation(ConversationCreateRequest req);

  /** Tạo conversation private giữa 2 người khi kết bạn (được gọi từ event listener) */
  ConversationResponse createConversationForFriends(Long userId1, Long userId2);

  ConversationSummaryResponse updateConversation(
      Long currentUserId, UpdateConversationRequest request);

  ConversationSummaryResponse addMembersToGroup(Long currentUserId, AddMembersRequest request);

  ConversationSummaryResponse removeMemberFromGroup(
      Long currentUserId, Long conversationId, Long userIdToRemove);

  void leaveConversation(Long currentUserId, Long conversationId);

  ConversationSummaryResponse updateMemberRole(Long currentUserId, UpdateMemberRoleRequest request);

  List<ConversationSummaryResponse> getUserConversations(Long userId);

  ConversationSummaryResponse getConversationById(Long currentUserId, Long conversationId);

  void markMessageAsRead(Long userId, MarkReadRequest request);

  void addLabelToConversation(Long roomId, ChatLabel label);

  void removeLabelFromConversation(Long roomId, ChatLabel label);
}
