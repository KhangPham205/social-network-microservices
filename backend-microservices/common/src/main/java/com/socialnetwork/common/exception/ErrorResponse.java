package com.socialnetwork.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Uniform error body returned by every service.
 *
 * @param statusCode HTTP status
 * @param error HTTP reason phrase
 * @param message human readable message (never an internal stack trace for 5xx)
 * @param path request path
 * @param details optional field-level validation errors
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int statusCode,
    String error,
    String message,
    String path,
    Instant timestamp,
    Map<String, String> details) {

  public static ErrorResponse of(HttpStatus status, String message, String path) {
    return new ErrorResponse(
        status.value(), status.getReasonPhrase(), message, path, Instant.now(), null);
  }

  public static ErrorResponse of(
      HttpStatus status, String message, String path, Map<String, String> details) {
    return new ErrorResponse(
        status.value(), status.getReasonPhrase(), message, path, Instant.now(), details);
  }
}
