package com.socialnetwork.auth_service.controller;

import com.socialnetwork.auth_service.dto.AuthCredentialDto;
import com.socialnetwork.auth_service.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;

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
}