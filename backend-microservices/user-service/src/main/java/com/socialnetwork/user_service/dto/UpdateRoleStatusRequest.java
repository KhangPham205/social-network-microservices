package com.socialnetwork.user_service.dto;

import com.socialnetwork.common.vo.AccountStatus;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoleStatusRequest {
  private AccountStatus status;
  private Set<String> roles;
}
