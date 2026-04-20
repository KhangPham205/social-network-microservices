package com.socialnetwork.chat_service.service;

import com.socialnetwork.chat_service.dto.*;
import java.util.List;

public interface ConversationService {
  ConversationResponse createConversation(ConversationCreateRequest req);

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
}
