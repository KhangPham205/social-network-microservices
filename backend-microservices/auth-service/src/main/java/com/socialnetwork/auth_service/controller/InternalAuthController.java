package com.socialnetwork.auth_service.controller;

import com.socialnetwork.auth_service.dto.AuthCredentialDto;
import com.socialnetwork.auth_service.dto.UpdateRoleStatusRequest;
import com.socialnetwork.auth_service.service.AuthService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/internal")
@RequiredArgsConstructor
public class InternalAuthController {

  private final AuthService authService; // Service quản lý UserCredential của bạn

  @GetMapping("/credentials/{id}")
  public ResponseEntity<AuthCredentialDto> getCredential(@PathVariable("id") Long id) {
    return ResponseEntity.ok(authService.getCredentialById(id));
  }

  @PostMapping("/credentials/batch")
  public ResponseEntity<List<AuthCredentialDto>> getCredentialsBatch(@RequestBody List<Long> ids) {
    return ResponseEntity.ok(authService.getCredentialsByIds(ids));
  }

  @PutMapping("/credentials/{id}")
  public ResponseEntity<Void> updateRoleAndStatus(
      @PathVariable("id") Long id, @RequestBody UpdateRoleStatusRequest request) {
    authService.updateRoleAndStatus(id, request.getRoles(), request.getStatus());
    return ResponseEntity.ok().build();
  }
}
