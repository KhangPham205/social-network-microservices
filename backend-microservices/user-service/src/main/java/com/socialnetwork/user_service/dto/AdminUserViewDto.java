package com.socialnetwork.user_service.dto;

import com.socialnetwork.common.vo.AccountStatus;
import java.time.Instant;
import java.util.Set;
import lombok.Data;

/** Profile of this service merged with the credential owned by auth-service. */
@Data
public class AdminUserViewDto {
  private Long id;
  private String displayName;
  private String avatarUrl;

  // From auth-service (UserCredential)
  private Long credentialId;
  private String username;
  private String email;
  private AccountStatus status;
  private Set<String> roles;

  // From the local UserInfo
  private String bio;
  private Instant dateOfBirth;
  private String favorites;
}
