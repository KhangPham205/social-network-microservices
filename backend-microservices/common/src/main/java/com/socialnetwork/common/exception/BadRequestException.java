package com.socialnetwork.common.exception;

import org.springframework.http.HttpStatus;

/** Mapped to HTTP BAD_REQUEST by {@link GlobalExceptionHandler}. */
public class BadRequestException extends ApiException {
  public BadRequestException(String message) {
    super(HttpStatus.BAD_REQUEST, message);
  }
}
