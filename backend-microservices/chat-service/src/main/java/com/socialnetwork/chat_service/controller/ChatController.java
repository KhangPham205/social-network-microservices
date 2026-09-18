package com.socialnetwork.chat_service.controller;

import com.socialnetwork.chat_service.dto.MarkReadRequest;
import com.socialnetwork.chat_service.dto.MessageRequest;
import com.socialnetwork.chat_service.service.ConversationService;
import com.socialnetwork.chat_service.service.MessageService;
import com.socialnetwork.common.constants.WebSocketConstants;
import com.socialnetwork.common.exception.BadRequestException;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

  private final MessageService messageService;
  private final ConversationService conversationService;

  /** 📌 Gửi tin nhắn text qua WebSocket FE chỉ gửi content + conversationId + replyToId */
  @MessageMapping(WebSocketConstants.CHAT_SEND)
  public void handleChatMessage(@Payload MessageRequest messageRequest, Principal principal) {

    if (principal == null) {
      throw new BadRequestException("Unauthenticated WebSocket request.");
    }

    String senderId = (String) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
    Long uid = Long.parseLong(senderId);

    messageService.sendMessageAs(uid, messageRequest);

    log.info(
        "User {} sent WS message in conversation {}", senderId, messageRequest.getConversationId());
  }

  /** 📌 WS event khi user join (typing indicator / online presence) */
  @MessageMapping(WebSocketConstants.CHAT_ADD_USER)
  public String addUser(
      @Payload MessageRequest messageRequest, SimpMessageHeaderAccessor headerAccessor) {
    headerAccessor.getSessionAttributes().put("username", messageRequest.getContent());
    return messageRequest.getContent() + " joined the chat";
  }

  /** Nhận sự kiện "Đã đọc" từ Client */
  @MessageMapping("/chat.read")
  public void markAsRead(@Payload MarkReadRequest request, Principal principal) {
    Long userId = Long.parseLong(principal.getName());

    // Gọi Service xử lý logic lưu DB
    conversationService.markMessageAsRead(userId, request);
  }
}
