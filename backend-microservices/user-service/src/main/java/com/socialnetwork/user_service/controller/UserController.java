package com.socialnetwork.user_service.controller;

import com.socialnetwork.user_service.dto.FollowResponse;
import com.socialnetwork.user_service.dto.UpdateProfileRequest;
import com.socialnetwork.user_service.dto.UserProfileDto;
import com.socialnetwork.user_service.dto.UserRelationDto;
import com.socialnetwork.user_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vo.PageVO;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  // Xem profile của chính mình
  @GetMapping("/me")
  public ResponseEntity<UserProfileDto> getMyProfile() {
    Long myId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    return ResponseEntity.ok(userService.getProfile(myId));
  }

  // Xem profile của người khác
  @GetMapping("/{userId}")
  public ResponseEntity<UserProfileDto> getUserProfile(@PathVariable("userId") Long userId) {
    return ResponseEntity.ok(userService.getProfile(userId));
  }

  // Cập nhật profile
  @PutMapping("/me")
  public ResponseEntity<UserProfileDto> updateProfile(@RequestBody UpdateProfileRequest request) {
    return ResponseEntity.ok(userService.updateMyProfile(request));
  }

  @GetMapping("/search")
  public ResponseEntity<PageVO<UserRelationDto>> searchUsers(
      @RequestParam(name = "filter", required = false) String filter,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(userService.searchUsers(filter, pageable));
  }

  @PostMapping("/follow")
  public ResponseEntity<FollowResponse> follow(@RequestParam(name = "targetId") Long targetId) {
    return ResponseEntity.ok(userService.followUser(targetId));
  }

  @DeleteMapping("/unfollow")
  public ResponseEntity<FollowResponse> unfollow(@RequestParam(name = "targetId") Long targetId) {
    return ResponseEntity.ok(userService.unfollowUser(targetId));
  }

  @GetMapping("/{id}/followers")
  public ResponseEntity<PageVO<UserRelationDto>> getFollowers(
      @PathVariable(name = "id") Long id,
      @RequestParam(name = "filter", required = false) String filter,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(userService.getFollowersPaged(id, filter, pageable));
  }

  @GetMapping("/{id}/following")
  public ResponseEntity<PageVO<UserRelationDto>> getFollowing(
      @PathVariable(name = "id") Long id,
      @RequestParam(name = "filter", required = false) String filter,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(userService.getFollowingPaged(id, filter, pageable));
  }

  @GetMapping("/{id}/relation-status")
  public ResponseEntity<UserRelationDto> getRelationStatus(@PathVariable Long id) {
    return ResponseEntity.ok(userService.getRelationWithUser(id));
  }
}
