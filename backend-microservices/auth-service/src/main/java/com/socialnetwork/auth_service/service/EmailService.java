package com.socialnetwork.auth_service.service;

import com.socialnetwork.auth_service.enums.OtpType;

public interface EmailService {

  /**
   * Sends a one-time code.
   *
   * @throws com.socialnetwork.auth_service.exception.MailDeliveryException when SMTP fails
   */
  void sendOtp(String to, OtpType otpType, String otp);
}
