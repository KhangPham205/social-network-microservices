package com.socialnetwork.chat_service.service;

import com.socialnetwork.chat_service.dto.MessageRequest;
import com.socialnetwork.chat_service.dto.MessageResponse;
import com.socialnetwork.common.dto.MessageModerationView;
import com.socialnetwork.common.vo.CursorPage;
import java.util.List;

public interface MessageService {

  /** REST path: sender is the authenticated user. */
  MessageResponse sendMessage(MessageRequest req);

  /** STOMP path: sender is the session principal. */
  MessageResponse sendMessageAs(Long senderId, MessageRequest req);

  CursorPage<MessageResponse> getMessagesCursor(Long conversationId, String before, int limit);

  void softDeleteMessage(String messageId);

  /** Applies a BLOCK / UNBLOCK command coming from moderation-service. */
  void applySystemBan(String messageId, boolean banned);

  MessageModerationView getModerationView(String messageId);

  Long getMessageOwnerId(String messageId);

  List<MessageModerationView> getModerationViews(List<String> messageIds);
}
