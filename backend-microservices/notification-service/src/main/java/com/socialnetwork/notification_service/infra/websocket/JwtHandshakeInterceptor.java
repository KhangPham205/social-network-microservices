package com.socialnetwork.notification_service.infra.websocket;

import static com.socialnetwork.common.constants.SecurityConstants.BEARER_PREFIX;
import static com.socialnetwork.common.constants.SecurityConstants.JWT_COOKIE;

import com.socialnetwork.common.security.JwtPrincipal;
import com.socialnetwork.common.security.JwtValidator;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Authenticates the STOMP handshake. The token is read from the {@code jwt} cookie, the {@code
 * Authorization: Bearer} header or the {@code token} query parameter; on failure the upgrade is
 * rejected with 401. Token material is never logged.
 */
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

    Optional<JwtPrincipal> principal =
        resolveToken(request).flatMap(jwtValidator::authenticate);

    if (principal.isEmpty()) {
      log.debug("WebSocket handshake rejected: missing or invalid token");
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    attributes.put(WebSocketAttributes.USER_ID, principal.get().userId());
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {}

  static Optional<String> resolveToken(ServerHttpRequest request) {
    String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return nonBlank(header.substring(BEARER_PREFIX.length()));
    }

    if (request instanceof ServletServerHttpRequest servletRequest) {
      HttpServletRequest http = servletRequest.getServletRequest();
      Cookie[] cookies = http.getCookies();
      if (cookies != null) {
        for (Cookie cookie : cookies) {
          if (JWT_COOKIE.equals(cookie.getName())) {
            Optional<String> value = nonBlank(cookie.getValue());
            if (value.isPresent()) {
              return value;
            }
          }
        }
      }
      return nonBlank(http.getParameter(WebSocketAttributes.TOKEN_PARAM));
    }

    String query = request.getURI().getQuery();
    if (query != null) {
      for (String pair : query.split("&")) {
        int eq = pair.indexOf('=');
        if (eq > 0 && WebSocketAttributes.TOKEN_PARAM.equals(pair.substring(0, eq))) {
          return nonBlank(pair.substring(eq + 1));
        }
      }
    }
    return Optional.empty();
  }

  private static Optional<String> nonBlank(String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
  }
}
