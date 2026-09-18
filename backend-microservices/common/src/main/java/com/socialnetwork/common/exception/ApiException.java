package com.socialnetwork.common.exception;

import org.springframework.http.HttpStatus;

/** Base class of every business exception; carries the HTTP status it should be rendered with. */
public abstract class ApiException extends RuntimeException {

  private final HttpStatus status;

  protected ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
