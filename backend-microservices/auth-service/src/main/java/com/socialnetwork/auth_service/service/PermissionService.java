package com.socialnetwork.auth_service.service;

import com.socialnetwork.auth_service.dto.PermissionRequest;
import com.socialnetwork.auth_service.dto.PermissionResponse;
import java.util.List;

public interface PermissionService {

  PermissionResponse create(PermissionRequest request);

  List<PermissionResponse> getAll();

  PermissionResponse update(Long id, PermissionRequest request);

  void delete(Long id);
}
