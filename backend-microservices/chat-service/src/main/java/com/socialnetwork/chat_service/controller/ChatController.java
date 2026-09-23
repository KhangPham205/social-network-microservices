package com.socialnetwork.chat_service.controller;

import com.socialnetwork.chat_service.dto.MarkReadRequest;
import com.socialnetwork.chat_service.dto.MessageRequest;
import com.socialnetwork.chat_service.service.ConversationService;
import com.socialnetwork.chat_service.service.MessageService;
import com.socialnetwork.common.constants.WebSocketConstants;
import com.socialnetwork.common.exception.AccessDeniedException;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * STOMP entry points. Destinations are declared without the {@code /app} prefix, which Spring
 * strips before matching. The principal is the user id set during the handshake.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

  private final MessageService messageService;
  private final ConversationService conversationService;

  @MessageMapping(WebSocketConstants.CHAT_SEND)
  public void handleChatMessage(@Valid @Payload MessageRequest request, Principal principal) {
    messageService.sendMessageAs(userIdOf(principal), request);
  }

  @MessageMapping(WebSocketConstants.CHAT_READ)
  public void markAsRead(@Valid @Payload MarkReadRequest request, Principal principal) {
    conversationService.markMessageAsRead(userIdOf(principal), request);
  }

  private static Long userIdOf(Principal principal) {
    if (principal == null) {
      throw new AccessDeniedException("Unauthenticated WebSocket session");
    }
    try {
      return Long.valueOf(principal.getName());
    } catch (NumberFormatException e) {
      throw new AccessDeniedException("WebSocket session carries no user id");
    }
  }
}
