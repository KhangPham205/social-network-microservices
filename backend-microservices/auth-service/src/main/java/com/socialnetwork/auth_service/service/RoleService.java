package com.socialnetwork.auth_service.service;

import com.socialnetwork.auth_service.model.Role;
import java.util.List;

public interface RoleService {
  Role assignPermission(Long roleId, Long permissionId);

  Role removePermission(Long roleId, Long permissionId);

  List<Role> getAll();
}
