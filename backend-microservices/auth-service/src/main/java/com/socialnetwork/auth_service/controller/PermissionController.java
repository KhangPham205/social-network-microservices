package com.socialnetwork.auth_service.controller;

import com.socialnetwork.auth_service.dto.PermissionRequest;
import com.socialnetwork.auth_service.model.Permission;
import com.socialnetwork.auth_service.service.PermissionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/permissions")
@RequiredArgsConstructor
public class PermissionController {

  private final PermissionService permissionService;

  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public ResponseEntity<Permission> create(@RequestBody PermissionRequest permission) {
    return ResponseEntity.ok(permissionService.create(permission));
  }

  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  public ResponseEntity<List<Permission>> getAll() {
    return ResponseEntity.ok(permissionService.getAll());
  }

  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/{id}")
  public ResponseEntity<Permission> update(
      @PathVariable Long id, @RequestBody PermissionRequest permission) {
    return ResponseEntity.ok(permissionService.update(id, permission));
  }

  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    permissionService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
