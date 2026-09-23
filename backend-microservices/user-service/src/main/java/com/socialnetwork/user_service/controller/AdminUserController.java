package com.socialnetwork.user_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.user_service.dto.AdminUpdateUserRequest;
import com.socialnetwork.user_service.dto.AdminUserViewDto;
import com.socialnetwork.user_service.service.Neo4jMigrationService;
import com.socialnetwork.user_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Back-office endpoints. The whole path already requires {@code ROLE_ADMIN} (see SecurityConfig). */
@RestController
@RequestMapping(ApiConstants.USERS + "/admin")
@RequiredArgsConstructor
public class AdminUserController {

  private final UserService userService;
  private final Neo4jMigrationService neo4jMigrationService;

  @GetMapping
  @PreAuthorize("hasAuthority('USER:READ_ALL')")
  public ResponseEntity<PageVO<AdminUserViewDto>> getAllUsers(
      @RequestParam(value = "filter", required = false) String filter,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(userService.getAllUsersForAdmin(filter, pageable));
  }

  @GetMapping("/{userId}")
  @PreAuthorize("hasAuthority('USER:READ_ALL')")
  public ResponseEntity<AdminUserViewDto> getUserByIdAsAdmin(@PathVariable("userId") Long userId) {
    return ResponseEntity.ok(userService.getUserByIdAsAdmin(userId));
  }

  @PutMapping("/{userId}")
  @PreAuthorize("hasAuthority('USER:UPDATE_ANY')")
  public ResponseEntity<AdminUserViewDto> updateUserAsAdmin(
      @PathVariable("userId") Long userId, @RequestBody AdminUpdateUserRequest request) {
    return ResponseEntity.ok(userService.updateUserAsAdmin(userId, request));
  }

  /** Rebuilds the recommendation graph from the relational data. Admin-only maintenance task. */
  @PostMapping("/neo4j/sync")
  public ResponseEntity<String> syncNeo4j() {
    return ResponseEntity.ok(neo4jMigrationService.runMigration());
  }
}
