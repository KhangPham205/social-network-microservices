package com.socialnetwork.notification_service.infra.websocket;

import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import security.JwtValidator;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

  private final JwtValidator jwtValidator;

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    try {
      URI uri = request.getURI();
      String query = uri.getQuery();

      if (query == null || !query.startsWith("token=")) {
        log.warn("⚠️ WebSocket Handshake: No token provided");
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
      }

      String token = query.substring("token=".length());

      if (!jwtValidator.validateToken(token)) {
        log.warn("⚠️ WebSocket Handshake: Invalid token");
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
      }

      Long userId = jwtValidator.extractUserId(token);
      attributes.put("userId", userId);
      log.info("✅ WebSocket Handshake Success for user: {}", userId);

      return true;

    } catch (Exception e) {
      log.error("❌ WebSocket Handshake Failed: {}", e.getMessage());
      response.setStatusCode(HttpStatus.FORBIDDEN);
      return false;
    }
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {}
}
