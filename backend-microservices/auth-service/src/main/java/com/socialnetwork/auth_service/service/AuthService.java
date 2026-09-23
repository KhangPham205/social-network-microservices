package com.socialnetwork.auth_service.service;

import com.socialnetwork.auth_service.dto.AuthCredentialDto;
import com.socialnetwork.auth_service.dto.CreateStaffRequest;
import com.socialnetwork.auth_service.dto.LoginRequest;
import com.socialnetwork.auth_service.dto.LoginResponse;
import com.socialnetwork.auth_service.dto.OtpVerificationRequest;
import com.socialnetwork.auth_service.dto.RegisterRequest;
import com.socialnetwork.auth_service.dto.RegisterResponse;
import com.socialnetwork.common.vo.AccountStatus;
import java.util.List;
import java.util.Set;
import org.springframework.lang.Nullable;

public interface AuthService {

  RegisterResponse register(RegisterRequest request);

  LoginResponse login(LoginRequest request);

  /**
   * Revokes every refresh token of the caller. The user is resolved from the refresh-token cookie
   * and from the access token, which may already be expired; both arguments may be absent, in which
   * case the call is a no-op.
   */
  void logout(@Nullable String accessToken, @Nullable String refreshToken);

  /** Generates and e-mails a fresh verification OTP for an unverified account. */
  void sendVerificationCode(String email);

  /** @return true when the code was accepted, false when it is wrong or expired */
  boolean verifyOtp(OtpVerificationRequest request);

  RegisterResponse createStaffAccount(CreateStaffRequest request);

  /** Internal/moderation update; null arguments are left unchanged. */
  void updateRoleAndStatus(
      Long credentialId, @Nullable Set<String> roleNames, @Nullable AccountStatus status);

  AuthCredentialDto getCredentialById(Long id);

  List<AuthCredentialDto> getCredentialsByIds(List<Long> ids);
}
