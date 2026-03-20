package com.socialnetwork.user_service.controller;

import com.socialnetwork.user_service.dto.FriendshipResponse;
import com.socialnetwork.user_service.dto.UserRelationDto;
import com.socialnetwork.user_service.service.FriendshipService;
import com.socialnetwork.user_service.service.UserService;
import constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import utils.SecurityUtils;
import vo.PageVO;

@RestController
@RequestMapping("/api/users/friendship")
@RequiredArgsConstructor
public class FriendshipController {

  private final UserService userService; // Dùng để lấy CurrentUser
  private final FriendshipService friendshipService;

  @PostMapping("/send")
  public ResponseEntity<FriendshipResponse> sendRequest(
      @RequestParam(name = "targetId") Long targetId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(friendshipService.sendRequest(currentUserId, targetId));
  }

  @PostMapping("/unsend")
  public ResponseEntity<FriendshipResponse> unsendRequest(
      @RequestParam(name = "targetId") Long targetId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(friendshipService.unsendRequest(currentUserId, targetId));
  }

  @PostMapping("/accept")
  public ResponseEntity<FriendshipResponse> acceptRequest(
      @RequestParam(name = "requesterId") Long requesterId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(friendshipService.acceptRequest(requesterId, currentUserId));
  }

  @PostMapping("/reject")
  public ResponseEntity<FriendshipResponse> rejectRequest(
      @RequestParam(name = "requesterId") Long requesterId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(friendshipService.rejectRequest(requesterId, currentUserId));
  }

  @DeleteMapping("/unfriend")
  public ResponseEntity<FriendshipResponse> unfriend(
      @RequestParam(name = "friendId") Long friendId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(friendshipService.unfriend(currentUserId, friendId));
  }

  @PostMapping("/block")
  public ResponseEntity<FriendshipResponse> blockUser(
      @RequestParam(name = "targetId") Long targetId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(friendshipService.blockUser(currentUserId, targetId));
  }

  @DeleteMapping("/unblock")
  public ResponseEntity<FriendshipResponse> unblockUser(
      @RequestParam(name = "targetId") Long targetId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(friendshipService.unblockUser(currentUserId, targetId));
  }

  // --- Lấy danh sách ---

  @GetMapping("/sent")
  public ResponseEntity<PageVO<UserRelationDto>> getSentRequests(
      @ParameterObject Pageable pageable,
      @RequestParam(name = "filter", required = false) String filter) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(friendshipService.getSentRequests(currentUserId, filter, pageable));
  }

  @GetMapping
  public ResponseEntity<PageVO<UserRelationDto>> getMyFriends(
      @ParameterObject Pageable pageable,
      @RequestParam(name = "filter", required = false) String filter) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(friendshipService.getFriends(currentUserId, filter, pageable));
  }

  @GetMapping("/{userId}")
  public ResponseEntity<PageVO<UserRelationDto>> getFriends(
      @PathVariable(name = "userId") Long userId,
      @ParameterObject Pageable pageable,
      @RequestParam(name = "filter", required = false) String filter) {
    return ResponseEntity.ok(friendshipService.getFriends(userId, filter, pageable));
  }

  @GetMapping("/pending")
  public ResponseEntity<PageVO<UserRelationDto>> getPendingRequests(
      @ParameterObject Pageable pageable,
      @RequestParam(name = "filter", required = false) String filter) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(friendshipService.getPendingRequests(currentUserId, filter, pageable));
  }

  @GetMapping("/blocked")
  public ResponseEntity<PageVO<UserRelationDto>> getBlockedUsers(
      @ParameterObject Pageable pageable,
      @RequestParam(name = "filter", required = false) String filter) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(friendshipService.getBlockedUsers(currentUserId, filter, pageable));
  }
}
