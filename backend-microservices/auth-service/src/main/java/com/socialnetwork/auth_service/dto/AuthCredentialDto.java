package com.socialnetwork.auth_service.dto;

import com.socialnetwork.auth_service.enums.AccountStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthCredentialDto {
  private Long id;
  private String username;
  private String email;
  private AccountStatus status;
}
