package com.socialnetwork.auth_service.exception;

import com.socialnetwork.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 429: too many wrong OTP codes were submitted; the code has been invalidated. */
public class OtpAttemptsExceededException extends ApiException {

  public OtpAttemptsExceededException(String message) {
    super(HttpStatus.TOO_MANY_REQUESTS, message);
  }
}
