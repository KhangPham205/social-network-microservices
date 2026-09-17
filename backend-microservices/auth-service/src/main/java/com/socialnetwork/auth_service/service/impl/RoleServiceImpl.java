package com.socialnetwork.auth_service.service.impl;

import com.socialnetwork.auth_service.model.Permission;
import com.socialnetwork.auth_service.model.Role;
import com.socialnetwork.auth_service.repository.PermissionRepository;
import com.socialnetwork.auth_service.repository.RoleRepository;
import com.socialnetwork.auth_service.service.RoleService;
import exception.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;

  @Override
  public Role assignPermission(Long roleId, Long permissionId) {
    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    Permission permission =
        permissionRepository
            .findById(permissionId)
            .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));
    role.getPermissions().add(permission);
    return roleRepository.save(role);
  }

  @Override
  public Role removePermission(Long roleId, Long permissionId) {
    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    Permission permission =
        permissionRepository
            .findById(permissionId)
            .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));
    role.getPermissions().remove(permission);
    return roleRepository.save(role);
  }

  @Override
  public List<Role> getAll() {
    return roleRepository.findAll();
  }
}
