package com.socialnetwork.auth_service.service.impl;

import java.security.SecureRandom;

/** 6-digit one-time codes shared by e-mail verification and password reset. */
final class OtpCodes {

  /** Wrong submissions allowed per code before it is invalidated. */
  static final int MAX_ATTEMPTS = 5;

  private static final SecureRandom RANDOM = new SecureRandom();

  private OtpCodes() {}

  static String generate() {
    return String.format("%06d", RANDOM.nextInt(1_000_000));
  }
}
