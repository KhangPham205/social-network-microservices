package com.socialnetwork.user_service.dto;

import com.socialnetwork.common.vo.AccountStatus;
import java.util.Set;
import lombok.Data;

@Data
public class AdminUpdateUserRequest {
  private String displayName;
  private String bio;
  private AccountStatus status;
  private Set<String> roles;
}
