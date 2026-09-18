package com.socialnetwork.common.exception;

import org.springframework.http.HttpStatus;

/** Mapped to HTTP CONFLICT by {@link GlobalExceptionHandler}. */
public class ConflictException extends ApiException {
  public ConflictException(String message) {
    super(HttpStatus.CONFLICT, message);
  }
}
