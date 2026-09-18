package com.socialnetwork.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateStaffRequest {
  @NotBlank private String username;
  @NotBlank private String password;
  @NotBlank @Email private String email;
  private String fullname;

  /** Role name without prefix, e.g. "ADMIN" or "MODERATOR". */
  @NotBlank private String roleName;
}
