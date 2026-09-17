package com.socialnetwork.notification_service.infra.websocket;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

  private final SimpMessagingTemplate messagingTemplate;

  @EventListener
  public void handleWebSocketConnectListener(SessionConnectedEvent event) {
    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
    Principal user = headerAccessor.getUser();

    if (user != null) {
      String userId = user.getName();
      log.info("✅ User Connected: {}", userId);

      Map<String, Object> statusUpdate =
          Map.of("type", "USER_ONLINE", "userId", userId, "timestamp", System.currentTimeMillis());
      messagingTemplate.convertAndSend("/topic/public", (Object) statusUpdate);
    }
  }

  @EventListener
  public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
    Principal userPrincipal = headerAccessor.getUser();

    if (userPrincipal != null) {
      String userIdStr = userPrincipal.getName();
      Long userId = Long.parseLong(userIdStr);
      log.info("❌ User Disconnected: {}", userId);

      Map<String, Object> statusUpdate =
          Map.of("type", "USER_OFFLINE", "userId", userId, "timestamp", Instant.now().toString());
      messagingTemplate.convertAndSend("/topic/public", (Object) statusUpdate);
    }
  }
}
