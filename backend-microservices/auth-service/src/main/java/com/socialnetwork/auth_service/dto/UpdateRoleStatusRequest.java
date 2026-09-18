package com.socialnetwork.auth_service.dto;

import com.socialnetwork.common.vo.AccountStatus;
import java.util.Set;
import lombok.Data;

/** Internal API body for {@code PUT /internal/credentials/{id}}; null fields are left unchanged. */
@Data
public class UpdateRoleStatusRequest {
  private AccountStatus status;
  private Set<String> roles;
}
