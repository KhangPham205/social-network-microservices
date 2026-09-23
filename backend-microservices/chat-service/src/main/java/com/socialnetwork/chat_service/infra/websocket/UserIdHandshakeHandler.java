package com.socialnetwork.chat_service.infra.websocket;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * Turns the identity captured by {@link JwtHandshakeInterceptor} into the session
 * {@link Principal}: name is the user id, authorities are the ones carried by the token.
 */
public class UserIdHandshakeHandler extends DefaultHandshakeHandler {

  @Override
  @SuppressWarnings("unchecked")
  protected Principal determineUser(
      ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
    Object userId = attributes.get(WebSocketAttributes.USER_ID);
    if (userId == null) {
      return null;
    }
    Object authorities = attributes.get(WebSocketAttributes.AUTHORITIES);
    List<GrantedAuthority> granted =
        authorities instanceof List<?> list ? (List<GrantedAuthority>) list : List.of();
    return new UsernamePasswordAuthenticationToken(userId.toString(), null, granted);
  }
}
