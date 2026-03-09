package com.socialnetwork.auth_service.service.impl;

import com.kt.social.auth.dto.PermissionRequest; // <-- Dùng DTO
import com.kt.social.auth.model.Permission;
import com.kt.social.auth.repository.PermissionRepository;
import com.kt.social.auth.service.PermissionService;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;

    @Override
    public Permission create(PermissionRequest request) {
        String resource = request.getResource().toUpperCase();
        String action = request.getAction().toUpperCase();
        String name = resource + ":" + action; // Tự động tạo tên chuẩn

        if (permissionRepository.existsByName(name)) {
            throw new BadRequestException("Permission already exists: " + name);
        }

        Permission permission = Permission.builder()
                .resource(resource)
                .action(action)
                .name(name) // Gán tên chuẩn
                .description(request.getDescription())
                .build();

        return permissionRepository.save(permission);
    }

    @Override
    public List<Permission> getAll() {
        return permissionRepository.findAll();
    }

    @Override
    public Permission update(Long id, PermissionRequest request) {
        Permission existingPermission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));

        String resource = request.getResource().toUpperCase();
        String action = request.getAction().toUpperCase();
        String name = resource + ":" + action;

        if (!existingPermission.getName().equals(name) && permissionRepository.existsByName(name)) {
            throw new BadRequestException("Permission name already exists: " + name);
        }

        existingPermission.setResource(resource);
        existingPermission.setAction(action);
        existingPermission.setName(name);
        existingPermission.setDescription(request.getDescription());

        return permissionRepository.save(existingPermission);
    }

    @Override
    public void delete(Long id) {
        if (!permissionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy quyền (Permission) với ID: " + id);
        }
        permissionRepository.deleteById(id);
    }
}