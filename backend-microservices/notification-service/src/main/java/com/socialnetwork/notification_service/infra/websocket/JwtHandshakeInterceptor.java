package com.socialnetwork.notification_service.infra.websocket;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
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
      Map<String, Object> attributes)
      throws Exception {

    if (request instanceof ServletServerHttpRequest) {
      ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
      Cookie[] cookies = servletRequest.getServletRequest().getCookies();

      String token = null;
      if (cookies != null) {
        for (Cookie cookie : cookies) {
          if ("jwt".equals(cookie.getName())) {
            token = cookie.getValue();
            break;
          }
        }
      }

      if (token != null && jwtValidator.validateToken(token)) {
        // Lấy userId ra và lưu vào attributes để WebSocket xài
        Long userId = jwtValidator.extractUserId(token);
        attributes.put("userId", userId);
        return true;
      }
    }

    return false;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {}
}
