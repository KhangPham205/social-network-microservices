package com.socialnetwork.auth_service.service;

import com.socialnetwork.auth_service.dto.PasswordResetRequest;

public interface PasswordResetService {
  void sendResetCode(PasswordResetRequest request);
}
