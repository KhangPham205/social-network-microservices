package com.socialnetwork.auth_service.service.impl;

import com.socialnetwork.auth_service.model.UserCredential;
import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.vo.AccountStatus;

/** Login and token refresh are only allowed for ACTIVE accounts. */
final class AccountStatusGuard {

  private AccountStatusGuard() {}

  static void assertCanAuthenticate(UserCredential user) {
    AccountStatus status = user.getStatus();
    if (status == AccountStatus.ACTIVE) {
      return;
    }
    throw new AccessDeniedException(
        switch (status) {
          case WAITING, PENDING ->
              "Account is not verified yet. Please verify your e-mail before logging in.";
          case BLOCKED -> "Account has been blocked.";
          case NOT_AUTHORIZED -> "Account is not authorized to log in.";
          case NOT_SOLVED ->
              "Account registration could not be completed. Please contact support.";
          default -> "Account is not allowed to log in (status " + status + ").";
        });
  }
}
