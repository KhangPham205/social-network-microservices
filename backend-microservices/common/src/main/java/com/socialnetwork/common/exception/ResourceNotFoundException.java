package com.socialnetwork.common.exception;

import org.springframework.http.HttpStatus;

/** Mapped to HTTP NOT_FOUND by {@link GlobalExceptionHandler}. */
public class ResourceNotFoundException extends ApiException {
  public ResourceNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, message);
  }
}
