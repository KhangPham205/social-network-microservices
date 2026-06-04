package com.socialnetwork.auth_service.controller;

import com.socialnetwork.auth_service.dto.CreateStaffRequest;
import com.socialnetwork.auth_service.dto.RegisterResponse;
import com.socialnetwork.auth_service.dto.UpdateRoleStatusRequest;
import com.socialnetwork.auth_service.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
public class AdminAuthController {

  private final AuthService authService;

  @PostMapping("/staff")
//  @PreAuthorize("hasAuthority('USER:CREATE')")
  public ResponseEntity<RegisterResponse> createStaff(@RequestBody CreateStaffRequest request) {
    return ResponseEntity.ok(authService.createStaffAccount(request));
  }
}
