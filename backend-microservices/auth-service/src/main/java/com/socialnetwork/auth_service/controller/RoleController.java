package com.socialnetwork.auth_service.controller;

import com.socialnetwork.auth_service.model.Role;
import com.socialnetwork.auth_service.service.RoleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class RoleController {

  private final RoleService roleService;

  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  public ResponseEntity<List<Role>> getAll() {
    return ResponseEntity.ok(roleService.getAll());
  }

  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{roleId}/permissions/{permissionId}")
  public ResponseEntity<Role> assignPermission(
      @PathVariable Long roleId, @PathVariable Long permissionId) {
    return ResponseEntity.ok(roleService.assignPermission(roleId, permissionId));
  }

  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{roleId}/permissions/{permissionId}")
  public ResponseEntity<Role> removePermission(
      @PathVariable Long roleId, @PathVariable Long permissionId) {
    return ResponseEntity.ok(roleService.removePermission(roleId, permissionId));
  }
}
