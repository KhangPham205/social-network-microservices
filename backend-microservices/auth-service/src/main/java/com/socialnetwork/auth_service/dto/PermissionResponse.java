package com.socialnetwork.auth_service.dto;

import com.socialnetwork.auth_service.model.Permission;

public record PermissionResponse(
    Long id, String resource, String action, String name, String description) {

  public static PermissionResponse from(Permission p) {
    return new PermissionResponse(
        p.getId(), p.getResource(), p.getAction(), p.getName(), p.getDescription());
  }
}
