package com.socialnetwork.auth_service.controller;

import com.socialnetwork.auth_service.config.SecurityConfig;
import com.socialnetwork.auth_service.dto.CreateStaffRequest;
import com.socialnetwork.auth_service.dto.RegisterResponse;
import com.socialnetwork.auth_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff provisioning. The whole path is restricted to {@code ROLE_ADMIN} by {@link SecurityConfig}. */
@RestController
@RequestMapping(SecurityConfig.ADMIN_PATH)
@RequiredArgsConstructor
public class AdminAuthController {

  private final AuthService authService;

  @PostMapping("/staff")
  public ResponseEntity<RegisterResponse> createStaff(
      @Valid @RequestBody CreateStaffRequest request) {
    return ResponseEntity.ok(authService.createStaffAccount(request));
  }
}
