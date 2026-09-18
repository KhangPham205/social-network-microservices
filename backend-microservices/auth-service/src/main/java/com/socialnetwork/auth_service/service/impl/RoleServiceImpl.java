package com.socialnetwork.auth_service.service.impl;

import com.socialnetwork.auth_service.dto.RoleResponse;
import com.socialnetwork.auth_service.model.Permission;
import com.socialnetwork.auth_service.model.Role;
import com.socialnetwork.auth_service.repository.PermissionRepository;
import com.socialnetwork.auth_service.repository.RoleRepository;
import com.socialnetwork.auth_service.service.RoleService;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;

  @Override
  @Transactional
  public RoleResponse assignPermission(Long roleId, Long permissionId) {
    Role role = findRole(roleId);
    Permission permission = findPermission(permissionId);
    role.getPermissions().add(permission);
    return RoleResponse.from(roleRepository.save(role));
  }

  @Override
  @Transactional
  public RoleResponse removePermission(Long roleId, Long permissionId) {
    Role role = findRole(roleId);
    findPermission(permissionId);
    role.getPermissions().removeIf(p -> permissionId.equals(p.getId()));
    return RoleResponse.from(roleRepository.save(role));
  }

  @Override
  @Transactional(readOnly = true)
  public List<RoleResponse> getAll() {
    return roleRepository.findAll().stream().map(RoleResponse::from).toList();
  }

  private Role findRole(Long roleId) {
    return roleRepository
        .findById(roleId)
        .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
  }

  private Permission findPermission(Long permissionId) {
    return permissionRepository
        .findById(permissionId)
        .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permissionId));
  }
}
