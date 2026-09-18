package com.socialnetwork.auth_service.dto;

import com.socialnetwork.common.vo.AccountStatus;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

/** Internal API view of a credential (CONTRACT section 4). */
@Data
@Builder
public class AuthCredentialDto {
  private Long id;
  private String username;
  private String email;
  private AccountStatus status;
  private Set<String> roles;
}
