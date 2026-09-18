package com.socialnetwork.auth_service.exception;

import com.socialnetwork.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 503: the OTP e-mail could not be handed to the SMTP server. */
public class MailDeliveryException extends ApiException {

  public MailDeliveryException(String message) {
    super(HttpStatus.SERVICE_UNAVAILABLE, message);
  }
}
