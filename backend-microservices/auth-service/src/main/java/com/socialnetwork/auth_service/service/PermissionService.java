package com.socialnetwork.auth_service.service;

import com.kt.social.auth.dto.PermissionRequest;
import com.kt.social.auth.model.Permission;
import java.util.List;

public interface PermissionService {
    Permission create(PermissionRequest request);
    List<Permission> getAll();
    Permission update(Long id, PermissionRequest request);
    void delete(Long id);
}