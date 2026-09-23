package com.socialnetwork.chat_service.service;

import com.socialnetwork.chat_service.dto.AddMembersRequest;
import com.socialnetwork.chat_service.dto.ConversationCreateRequest;
import com.socialnetwork.chat_service.dto.ConversationResponse;
import com.socialnetwork.chat_service.dto.ConversationSummaryResponse;
import com.socialnetwork.chat_service.dto.MarkReadRequest;
import com.socialnetwork.chat_service.dto.UpdateConversationRequest;
import com.socialnetwork.chat_service.dto.UpdateMemberRoleRequest;
import com.socialnetwork.chat_service.enums.ChatLabel;
import java.util.List;

public interface ConversationService {

  ConversationResponse createConversation(ConversationCreateRequest req);

  /** Idempotent: called on {@code FriendAcceptedEvent}; reopens an archived room if there is. */
  ConversationResponse createConversationForFriends(Long userId1, Long userId2);

  /** Idempotent: called from {@code FriendshipDeletedEvent}. */
  void archiveConversationForFriends(Long userId1, Long userId2);

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

  void addLabelToConversation(Long currentUserId, Long roomId, ChatLabel label);

  void removeLabelFromConversation(Long currentUserId, Long roomId, ChatLabel label);
}
