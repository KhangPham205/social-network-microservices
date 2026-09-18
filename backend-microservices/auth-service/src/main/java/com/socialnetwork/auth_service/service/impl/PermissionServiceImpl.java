package com.socialnetwork.auth_service.service.impl;

import com.socialnetwork.auth_service.dto.PermissionRequest;
import com.socialnetwork.auth_service.dto.PermissionResponse;
import com.socialnetwork.auth_service.model.Permission;
import com.socialnetwork.auth_service.repository.PermissionRepository;
import com.socialnetwork.auth_service.service.PermissionService;
import com.socialnetwork.common.exception.ConflictException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

  private final PermissionRepository permissionRepository;

  @Override
  @Transactional
  public PermissionResponse create(PermissionRequest request) {
    String name = Permission.nameOf(request.getResource(), request.getAction());
    if (permissionRepository.existsByName(name)) {
      throw new ConflictException("Permission already exists: " + name);
    }
    Permission permission =
        Permission.builder()
            .resource(request.getResource().toUpperCase())
            .action(request.getAction().toUpperCase())
            .name(name)
            .description(request.getDescription())
            .build();
    return PermissionResponse.from(permissionRepository.save(permission));
  }

  @Override
  @Transactional(readOnly = true)
  public List<PermissionResponse> getAll() {
    return permissionRepository.findAll().stream().map(PermissionResponse::from).toList();
  }

  @Override
  @Transactional
  public PermissionResponse update(Long id, PermissionRequest request) {
    Permission existing =
        permissionRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));

    String name = Permission.nameOf(request.getResource(), request.getAction());
    if (!existing.getName().equals(name) && permissionRepository.existsByName(name)) {
      throw new ConflictException("Permission already exists: " + name);
    }
    existing.setResource(request.getResource().toUpperCase());
    existing.setAction(request.getAction().toUpperCase());
    existing.setName(name);
    existing.setDescription(request.getDescription());
    return PermissionResponse.from(permissionRepository.save(existing));
  }

  @Override
  @Transactional
  public void delete(Long id) {
    if (!permissionRepository.existsById(id)) {
      throw new ResourceNotFoundException("Permission not found: " + id);
    }
    permissionRepository.deleteById(id);
  }
}
