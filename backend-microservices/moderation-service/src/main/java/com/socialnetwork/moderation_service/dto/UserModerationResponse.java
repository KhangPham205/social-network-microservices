package com.socialnetwork.moderation_service.dto;

import com.socialnetwork.common.vo.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A reported user in the admin list: identity from auth/user-service, counter from the local DB. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserModerationResponse {
  private Long userId;
  private String username;
  private String email;
  private String displayName;
  private String avatar;
  private AccountStatus status;
  private long violationCount;
}
