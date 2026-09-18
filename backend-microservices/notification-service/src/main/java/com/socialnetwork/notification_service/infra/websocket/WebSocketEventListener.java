package com.socialnetwork.notification_service.infra.websocket;

import com.socialnetwork.common.constants.WebSocketConstants;
import com.socialnetwork.notification_service.dto.PresenceEvent;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** Broadcasts presence changes on {@code /topic/public}. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

  private final SimpMessagingTemplate messagingTemplate;

  @EventListener
  public void onConnected(SessionConnectedEvent event) {
    Long userId = userIdOf(event.getUser());
    if (userId == null) {
      return;
    }
    log.info("User {} connected to the notification socket", userId);
    messagingTemplate.convertAndSend(WebSocketConstants.PUBLIC_TOPIC, PresenceEvent.online(userId));
  }

  @EventListener
  public void onDisconnected(SessionDisconnectEvent event) {
    Long userId = userIdOf(event.getUser());
    if (userId == null) {
      return;
    }
    log.info("User {} disconnected from the notification socket", userId);
    messagingTemplate.convertAndSend(WebSocketConstants.PUBLIC_TOPIC, PresenceEvent.offline(userId));
  }

  private static Long userIdOf(Principal principal) {
    if (principal == null) {
      return null;
    }
    try {
      return Long.valueOf(principal.getName());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
