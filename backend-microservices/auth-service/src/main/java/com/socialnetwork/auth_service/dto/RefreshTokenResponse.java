package com.socialnetwork.auth_service.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenResponse {
  private String message;
  private TokenResponse tokenResponse;

  /** Role names without the {@code ROLE_} prefix. */
  private List<String> roles;
}
