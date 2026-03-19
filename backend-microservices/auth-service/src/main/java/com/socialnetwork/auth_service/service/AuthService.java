package com.socialnetwork.auth_service.service;

import com.socialnetwork.auth_service.dto.*;

public interface AuthService {
  RegisterResponse register(RegisterRequest request);

  LoginResponse login(LoginRequest request);

  void logout(String accessToken);

  void sendVerificationCode(String email);

  void resendVerificationCode(String email);

  boolean verifyOtp(OtpVerificationRequest request);

  RegisterResponse createStaffAccount(CreateStaffRequest request);
}
