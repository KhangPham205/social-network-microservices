package com.socialnetwork.user_service.controller;

import com.socialnetwork.user_service.dto.AdminUpdateUserRequest;
import com.socialnetwork.user_service.dto.AdminUserViewDto;
import com.socialnetwork.user_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vo.PageVO;

@RestController
@RequestMapping("/api/v1/users/admin")
@RequiredArgsConstructor
public class AdminUserController {

  private final UserService userService;

  @GetMapping
  //  @PreAuthorize("hasAuthority('USER:READ_ALL')")
  public ResponseEntity<PageVO<AdminUserViewDto>> getAllUsers(
      @RequestParam(value = "filter", required = false) String filter,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(userService.getAllUsersForAdmin(filter, pageable));
  }

  @GetMapping("/{userId}")
  //  @PreAuthorize("hasAuthority('USER:READ_ALL')")
  public ResponseEntity<AdminUserViewDto> getUserByIdAsAdmin(@PathVariable("userId") Long userId) {
    return ResponseEntity.ok(
        userService.getUserByIdAsAdmin(userId)); // Gọi hàm Composition cho 1 user
  }

  @PutMapping("/{userId}")
  //  @PreAuthorize("hasAuthority('USER:UPDATE_ANY')")
  public ResponseEntity<AdminUserViewDto> updateUserAsAdmin(
      @PathVariable("userId") Long userId, @RequestBody AdminUpdateUserRequest request) {
    return ResponseEntity.ok(userService.updateUserAsAdmin(userId, request));
  }
}
