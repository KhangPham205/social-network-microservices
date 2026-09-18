package com.socialnetwork.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PermissionRequest {
  @NotBlank private String resource;
  @NotBlank private String action;
  private String description;
}
