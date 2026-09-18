package com.socialnetwork.notification_service.infra.websocket;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * Turns the user id stored by {@link JwtHandshakeInterceptor} into the session {@link Principal}
 * once, so {@code convertAndSendToUser(userId, ...)} resolves for the whole session.
 */
public class UserIdHandshakeHandler extends DefaultHandshakeHandler {

  @Override
  protected Principal determineUser(
      ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
    Object userId = attributes.get(WebSocketAttributes.USER_ID);
    if (userId == null) {
      return null;
    }
    return new UsernamePasswordAuthenticationToken(
        userId.toString(), null, Collections.emptyList());
  }
}
