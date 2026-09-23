package com.socialnetwork.auth_service.controller;

import com.socialnetwork.auth_service.dto.AuthCredentialDto;
import com.socialnetwork.auth_service.dto.UpdateRoleStatusRequest;
import com.socialnetwork.auth_service.service.AuthService;
import com.socialnetwork.common.constants.ApiConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API. Reachable only with the shared {@code X-Internal-Token} header, which the
 * common {@code JwtSecurityConfigurer} enforces for {@code /api/v1/*&#47;internal/**}.
 */
@RestController
@RequestMapping(ApiConstants.AUTH + ApiConstants.INTERNAL)
@RequiredArgsConstructor
public class InternalAuthController {

  private final AuthService authService;

  @GetMapping("/credentials/{id}")
  public ResponseEntity<AuthCredentialDto> getCredential(@PathVariable Long id) {
    return ResponseEntity.ok(authService.getCredentialById(id));
  }

  @PostMapping("/credentials/batch")
  public ResponseEntity<List<AuthCredentialDto>> getCredentialsBatch(@RequestBody List<Long> ids) {
    return ResponseEntity.ok(authService.getCredentialsByIds(ids));
  }

  @PutMapping("/credentials/{id}")
  public ResponseEntity<Void> updateRoleAndStatus(
      @PathVariable Long id, @RequestBody UpdateRoleStatusRequest request) {
    authService.updateRoleAndStatus(id, request.getRoles(), request.getStatus());
    return ResponseEntity.noContent().build();
  }
}
