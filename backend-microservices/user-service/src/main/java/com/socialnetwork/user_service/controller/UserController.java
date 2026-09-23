package com.socialnetwork.user_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.security.SecurityUtils;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.user_service.dto.FollowResponse;
import com.socialnetwork.user_service.dto.UpdateProfileRequest;
import com.socialnetwork.user_service.dto.UserProfileDto;
import com.socialnetwork.user_service.dto.UserRelationDto;
import com.socialnetwork.user_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.USERS)
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public ResponseEntity<UserProfileDto> getMyProfile() {
    return ResponseEntity.ok(userService.getProfile(SecurityUtils.getCurrentUserId()));
  }

  @GetMapping("/{userId}")
  public ResponseEntity<UserProfileDto> getUserProfile(@PathVariable("userId") Long userId) {
    return ResponseEntity.ok(userService.getVisibleProfile(userId));
  }

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

  @GetMapping("/{userId}/followers")
  public ResponseEntity<PageVO<UserRelationDto>> getFollowers(
      @PathVariable(name = "userId") Long userId,
      @RequestParam(name = "filter", required = false) String filter,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(userService.getFollowersPaged(userId, filter, pageable));
  }

  @GetMapping("/{userId}/following")
  public ResponseEntity<PageVO<UserRelationDto>> getFollowing(
      @PathVariable(name = "userId") Long userId,
      @RequestParam(name = "filter", required = false) String filter,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(userService.getFollowingPaged(userId, filter, pageable));
  }

  @GetMapping("/{userId}/relation-status")
  public ResponseEntity<UserRelationDto> getRelationStatus(@PathVariable("userId") Long userId) {
    return ResponseEntity.ok(userService.getRelationWithUser(userId));
  }
}
