package com.socialnetwork.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

/** 403 with an {@link com.socialnetwork.common.exception.ErrorResponse} body. */
@RequiredArgsConstructor
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    JsonAuthenticationEntryPoint.write(
        response,
        HttpStatus.FORBIDDEN,
        "You do not have permission to access this resource",
        request.getRequestURI(),
        objectMapper);
  }
}
