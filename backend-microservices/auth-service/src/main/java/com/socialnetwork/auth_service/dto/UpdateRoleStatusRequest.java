package com.socialnetwork.auth_service.dto;

import com.socialnetwork.auth_service.enums.AccountStatus;
import java.util.Set;
import lombok.Data;

@Data
public class UpdateRoleStatusRequest {
  private AccountStatus status;
  private Set<String> roles;
}
