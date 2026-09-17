package com.socialnetwork.chat_service.service;

import com.socialnetwork.chat_service.dto.MessageRequest;
import com.socialnetwork.chat_service.dto.MessageResponse;
import java.util.List;
import java.util.Map;
import vo.CursorPage;

public interface MessageService {
  Map<String, Object> sendMessage(MessageRequest req);

  void sendMessageAs(Long senderId, MessageRequest req);

  CursorPage<MessageResponse> getMessagesCursor(Long conversationId, String before, int limit);

  List<Map<String, Object>> getMessages(Long conversationId);

  void softDeleteMessage(String messageId);
}
