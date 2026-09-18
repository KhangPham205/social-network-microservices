package com.socialnetwork.common.exception;

import org.springframework.http.HttpStatus;

/** Mapped to HTTP FORBIDDEN by {@link GlobalExceptionHandler}. */
public class AccessDeniedException extends ApiException {
  public AccessDeniedException(String message) {
    super(HttpStatus.FORBIDDEN, message);
  }
}
