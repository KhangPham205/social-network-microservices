package com.socialnetwork.auth_service.service;

import com.socialnetwork.auth_service.dto.RoleResponse;
import java.util.List;

public interface RoleService {

  RoleResponse assignPermission(Long roleId, Long permissionId);

  RoleResponse removePermission(Long roleId, Long permissionId);

  List<RoleResponse> getAll();
}
