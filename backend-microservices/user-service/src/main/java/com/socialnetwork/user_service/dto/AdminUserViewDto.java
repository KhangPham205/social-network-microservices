package com.socialnetwork.user_service.dto;

import com.socialnetwork.user_service.enums.AccountStatus;
import java.time.Instant;
import java.util.Set;
import lombok.Data;

@Data
public class AdminUserViewDto {
  private Long id;
  private String displayName;
  private String avatarUrl;

  // Từ UserCredential
  private Long credentialId;
  private String username;
  private String email;
  private AccountStatus status;
  private Set<String> roles; // (Tên các Role)

  // Từ UserInfo
  private String bio;
  private Instant dateOfBirth;
  private String favorites;
}
