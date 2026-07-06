package com.socialnetwork.auth_service.service;

import com.socialnetwork.auth_service.dto.*;
import com.socialnetwork.auth_service.enums.AccountStatus;
import java.util.List;
import java.util.Set;

public interface AuthService {
  RegisterResponse register(RegisterRequest request);

  LoginResponse login(LoginRequest request);

  void logout(String accessToken);

  void sendVerificationCode(String email);

  void resendVerificationCode(String email);

  boolean verifyOtp(OtpVerificationRequest request);

  RegisterResponse createStaffAccount(CreateStaffRequest request);

  void updateRoleAndStatus(Long credentialId, Set<String> roleNames, AccountStatus status);

  AuthCredentialDto getCredentialById(Long id);

  List<AuthCredentialDto> getCredentialsByIds(List<Long> ids);
}
