package com.socialnetwork.auth_service.controller;

import com.socialnetwork.auth_service.config.SecurityConfig;
import com.socialnetwork.auth_service.dto.PermissionRequest;
import com.socialnetwork.auth_service.dto.PermissionResponse;
import com.socialnetwork.auth_service.service.PermissionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Permission catalogue. Restricted to {@code ROLE_ADMIN} by {@link SecurityConfig}. */
@RestController
@RequestMapping(SecurityConfig.ADMIN_PATH + "/permissions")
@RequiredArgsConstructor
public class PermissionController {

  private final PermissionService permissionService;

  @PostMapping
  public ResponseEntity<PermissionResponse> create(@Valid @RequestBody PermissionRequest request) {
    return ResponseEntity.ok(permissionService.create(request));
  }

  @GetMapping
  public ResponseEntity<List<PermissionResponse>> getAll() {
    return ResponseEntity.ok(permissionService.getAll());
  }

  @PutMapping("/{id}")
  public ResponseEntity<PermissionResponse> update(
      @PathVariable Long id, @Valid @RequestBody PermissionRequest request) {
    return ResponseEntity.ok(permissionService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    permissionService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
