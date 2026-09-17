package com.socialnetwork.auth_service.dto;

import lombok.Data;

@Data
public class PermissionRequest {
  private String resource;
  private String action;
  private String description;
}
