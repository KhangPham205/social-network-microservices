package com.socialnetwork.auth_service.dto;

import com.socialnetwork.auth_service.model.Role;
import java.util.Comparator;
import java.util.List;

public record RoleResponse(
    Long id, String name, String description, List<PermissionResponse> permissions) {

  public static RoleResponse from(Role role) {
    List<PermissionResponse> permissions =
        role.getPermissions().stream()
            .map(PermissionResponse::from)
            .sorted(Comparator.comparing(PermissionResponse::name))
            .toList();
    return new RoleResponse(role.getId(), role.getName(), role.getDescription(), permissions);
  }
}
