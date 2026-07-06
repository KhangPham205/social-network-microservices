package com.socialnetwork.auth_service.dto;

import com.socialnetwork.auth_service.enums.AccountStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class LoginResponse {
  private String id;
  private String email;
  private AccountStatus status;
  private List<String> roles;
  private TokenResponse token;
}
