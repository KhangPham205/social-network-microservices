package com.socialnetwork.user_service.dto;

import com.socialnetwork.user_service.enums.AccountStatus;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthCredentialDto {
  private Long id;
  private String username;
  private String email;
  private AccountStatus status;
  private Set<String> roles;
}
