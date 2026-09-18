package com.socialnetwork.common.exception;

import org.springframework.http.HttpStatus;

/** Mapped to HTTP UNAUTHORIZED by {@link GlobalExceptionHandler}. */
public class InvalidCredentialsException extends ApiException {
  public InvalidCredentialsException(String message) {
    super(HttpStatus.UNAUTHORIZED, message);
  }
}
