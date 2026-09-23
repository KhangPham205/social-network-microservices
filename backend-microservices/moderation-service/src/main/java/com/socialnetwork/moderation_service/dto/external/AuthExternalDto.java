package com.socialnetwork.moderation_service.dto.external;

import com.socialnetwork.common.vo.AccountStatus;
import lombok.Data;

/** Credential data returned by auth-service's internal API. */
@Data
public class AuthExternalDto {
  private Long id;
  private String username;
  private String email;
  private AccountStatus status;
}
