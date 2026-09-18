package com.socialnetwork.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates exceptions into {@link ErrorResponse}. Registered in every service by {@code
 * CommonWebAutoConfiguration}. Order matters only for the catch-all at the bottom: Spring picks the
 * most specific handler for the exception type.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private static final String GENERIC_MESSAGE = "Internal server error";

  // ---------------------------------------------------------------- business exceptions

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
    return build(ex.getStatus(), ex.getMessage(), req);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest req) {
    return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorResponse> handleIllegalState(
      IllegalStateException ex, HttpServletRequest req) {
    return build(HttpStatus.CONFLICT, ex.getMessage(), req);
  }

  // ---------------------------------------------------------------- security

  @ExceptionHandler({
    org.springframework.security.access.AccessDeniedException.class,
    AuthorizationDeniedException.class
  })
  public ResponseEntity<ErrorResponse> handleSpringAccessDenied(
      RuntimeException ex, HttpServletRequest req) {
    return build(HttpStatus.FORBIDDEN, "You do not have permission to access this resource", req);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(
      AuthenticationException ex, HttpServletRequest req) {
    return build(HttpStatus.UNAUTHORIZED, "Authentication required", req);
  }

  // ---------------------------------------------------------------- validation / binding

  @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
  public ResponseEntity<ErrorResponse> handleValidation(BindException ex, HttpServletRequest req) {
    Map<String, String> details = new LinkedHashMap<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      details.putIfAbsent(fe.getField(), fe.getDefaultMessage());
    }
    return build(HttpStatus.BAD_REQUEST, "Validation failed", req, details);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest req) {
    Map<String, String> details = new LinkedHashMap<>();
    ex.getConstraintViolations()
        .forEach(v -> details.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
    return build(HttpStatus.BAD_REQUEST, "Validation failed", req, details);
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    MissingServletRequestPartException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  public ResponseEntity<ErrorResponse> handleBadInput(Exception ex, HttpServletRequest req) {
    String message =
        ex instanceof MethodArgumentTypeMismatchException mismatch
            ? "Invalid value for parameter '" + mismatch.getName() + "'"
            : ex.getMessage();
    return build(HttpStatus.BAD_REQUEST, message, req);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleUploadSize(
      MaxUploadSizeExceededException ex, HttpServletRequest req) {
    return build(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file is too large", req);
  }

  // ---------------------------------------------------------------- routing

  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  public ResponseEntity<ErrorResponse> handleNotFound(Exception ex, HttpServletRequest req) {
    return build(HttpStatus.NOT_FOUND, "No endpoint " + req.getMethod() + " " + req.getRequestURI(), req);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
    return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), req);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMediaType(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
    return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), req);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorResponse> handleResponseStatus(
      ResponseStatusException ex, HttpServletRequest req) {
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
    return build(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR, ex.getReason(), req);
  }

  // ---------------------------------------------------------------- persistence

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest req) {
    log.warn("Data integrity violation on {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMostSpecificCause().getMessage());
    return build(HttpStatus.CONFLICT, "The request conflicts with existing data", req);
  }

  // ---------------------------------------------------------------- downstream services

  @ExceptionHandler(HttpStatusCodeException.class)
  public ResponseEntity<ErrorResponse> handleDownstream(
      HttpStatusCodeException ex, HttpServletRequest req) {
    HttpStatusCode code = ex.getStatusCode();
    if (code.value() == HttpStatus.NOT_FOUND.value()) {
      return build(HttpStatus.NOT_FOUND, "Referenced resource was not found", req);
    }
    log.error("Downstream call failed with {} on {} {}", code, req.getMethod(), req.getRequestURI(), ex);
    return build(HttpStatus.BAD_GATEWAY, "A dependent service rejected the request", req);
  }

  @ExceptionHandler(ResourceAccessException.class)
  public ResponseEntity<ErrorResponse> handleDownstreamUnavailable(
      ResourceAccessException ex, HttpServletRequest req) {
    log.error("Downstream service unavailable on {} {}", req.getMethod(), req.getRequestURI(), ex);
    return build(HttpStatus.SERVICE_UNAVAILABLE, "A dependent service is unavailable", req);
  }

  // ---------------------------------------------------------------- catch-all

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
    ResponseStatus annotated = AnnotationUtils.findAnnotation(ex.getClass(), ResponseStatus.class);
    if (annotated != null) {
      HttpStatus status = annotated.value() != HttpStatus.INTERNAL_SERVER_ERROR ? annotated.value() : annotated.code();
      String reason = annotated.reason().isBlank() ? ex.getMessage() : annotated.reason();
      return build(status, reason, req);
    }
    log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_MESSAGE, req);
  }

  // ---------------------------------------------------------------- helpers

  private static ResponseEntity<ErrorResponse> build(
      HttpStatus status, String message, HttpServletRequest req) {
    return ResponseEntity.status(status).body(ErrorResponse.of(status, message, req.getRequestURI()));
  }

  private static ResponseEntity<ErrorResponse> build(
      HttpStatus status, String message, HttpServletRequest req, Map<String, String> details) {
    return ResponseEntity.status(status)
        .body(ErrorResponse.of(status, message, req.getRequestURI(), details));
  }
}
