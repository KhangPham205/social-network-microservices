package com.socialnetwork.auth_service.controller;

import com.socialnetwork.auth_service.config.SecurityConfig;
import com.socialnetwork.auth_service.dto.RoleResponse;
import com.socialnetwork.auth_service.service.RoleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Role/permission assignment. Restricted to {@code ROLE_ADMIN} by {@link SecurityConfig}. */
@RestController
@RequestMapping(SecurityConfig.ADMIN_PATH + "/roles")
@RequiredArgsConstructor
public class RoleController {

  private final RoleService roleService;

  @GetMapping
  public ResponseEntity<List<RoleResponse>> getAll() {
    return ResponseEntity.ok(roleService.getAll());
  }

  @PostMapping("/{roleId}/permissions/{permissionId}")
  public ResponseEntity<RoleResponse> assignPermission(
      @PathVariable Long roleId, @PathVariable Long permissionId) {
    return ResponseEntity.ok(roleService.assignPermission(roleId, permissionId));
  }

  @DeleteMapping("/{roleId}/permissions/{permissionId}")
  public ResponseEntity<RoleResponse> removePermission(
      @PathVariable Long roleId, @PathVariable Long permissionId) {
    return ResponseEntity.ok(roleService.removePermission(roleId, permissionId));
  }
}
