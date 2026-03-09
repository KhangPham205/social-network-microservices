package com.socialnetwork.auth_service.service.impl;

import com.kt.social.auth.model.Permission;
import com.kt.social.auth.model.Role;
import com.kt.social.auth.repository.PermissionRepository;
import com.kt.social.auth.repository.RoleRepository;
import com.kt.social.auth.service.PermissionService;
import com.kt.social.auth.service.RoleService;
import com.kt.social.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Override
    public Role assignPermission(Long roleId, Long permissionId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));
        role.getPermissions().add(permission);
        return roleRepository.save(role);
    }

    @Override
    public Role removePermission(Long roleId, Long permissionId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));
        role.getPermissions().remove(permission);
        return roleRepository.save(role);
    }

    @Override
    public List<Role> getAll() {
        return roleRepository.findAll();
    }
}
