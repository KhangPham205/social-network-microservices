package com.socialnetwork.user_service.controller;

import com.socialnetwork.user_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/internal")
@RequiredArgsConstructor
public class InternalUserController {

  private final UserService userService;

  // API này không cần token (đã được cấu hình permitAll trong SecurityConfig)
  @PostMapping("/create")
  public ResponseEntity<Void> createEmptyProfile(
      @RequestParam("accountId") Long accountId, @RequestParam("displayName") String displayName) {

    userService.createDefaultProfile(accountId, displayName);
    return ResponseEntity.ok().build();
  }
}
