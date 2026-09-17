package com.socialnetwork.chat_service.infra.websocket;

import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StompPrincipalInterceptor implements ChannelInterceptor {

  @Override
  public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {

    StompHeaderAccessor accessor =
        StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null) return message;

    log.info("command={}", accessor.getCommand());
    log.info("sessionAttrs={}", accessor.getSessionAttributes());
    log.info("user={}", accessor.getUser());

    if (StompCommand.CONNECT.equals(accessor.getCommand())
        && accessor.getUser() == null
        && accessor.getSessionAttributes() != null) {

      Object userId = accessor.getSessionAttributes().get("userId");

      if (userId != null) {
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(
                userId.toString(), null, Collections.emptyList());

        accessor.setUser(auth);

        log.info("Principal set: {}", userId);
      }
    }

    return message;
  }
}
