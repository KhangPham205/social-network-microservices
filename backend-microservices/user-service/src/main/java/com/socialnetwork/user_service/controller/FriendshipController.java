package com.socialnetwork.user_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.security.SecurityUtils;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.user_service.dto.FriendshipResponse;
import com.socialnetwork.user_service.dto.UserRelationDto;
import com.socialnetwork.user_service.service.FriendshipService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.USERS + "/friendship")
@RequiredArgsConstructor
public class FriendshipController {

  private final FriendshipService friendshipService;

  @PostMapping("/send")
  public ResponseEntity<FriendshipResponse> sendRequest(
      @RequestParam(name = "targetId") Long targetId) {
    return ResponseEntity.ok(
        friendshipService.sendRequest(SecurityUtils.getCurrentUserId(), targetId));
  }

  @PostMapping("/unsend")
  public ResponseEntity<FriendshipResponse> unsendRequest(
      @RequestParam(name = "targetId") Long targetId) {
    return ResponseEntity.ok(
        friendshipService.unsendRequest(SecurityUtils.getCurrentUserId(), targetId));
  }

  @PostMapping("/accept")
  public ResponseEntity<FriendshipResponse> acceptRequest(
      @RequestParam(name = "requesterId") Long requesterId) {
    return ResponseEntity.ok(
        friendshipService.acceptRequest(requesterId, SecurityUtils.getCurrentUserId()));
  }

  @PostMapping("/reject")
  public ResponseEntity<FriendshipResponse> rejectRequest(
      @RequestParam(name = "requesterId") Long requesterId) {
    return ResponseEntity.ok(
        friendshipService.rejectRequest(requesterId, SecurityUtils.getCurrentUserId()));
  }

  @DeleteMapping("/unfriend")
  public ResponseEntity<FriendshipResponse> unfriend(
      @RequestParam(name = "friendId") Long friendId) {
    return ResponseEntity.ok(
        friendshipService.unfriend(SecurityUtils.getCurrentUserId(), friendId));
  }

  @PostMapping("/block")
  public ResponseEntity<FriendshipResponse> blockUser(
      @RequestParam(name = "targetId") Long targetId) {
    return ResponseEntity.ok(
        friendshipService.blockUser(SecurityUtils.getCurrentUserId(), targetId));
  }

  @DeleteMapping("/unblock")
  public ResponseEntity<FriendshipResponse> unblockUser(
      @RequestParam(name = "targetId") Long targetId) {
    return ResponseEntity.ok(
        friendshipService.unblockUser(SecurityUtils.getCurrentUserId(), targetId));
  }

  @GetMapping
  public ResponseEntity<PageVO<UserRelationDto>> getMyFriends(
      @ParameterObject Pageable pageable,
      @RequestParam(name = "filter", required = false) String filter) {
    return ResponseEntity.ok(
        friendshipService.getFriends(SecurityUtils.getCurrentUserId(), filter, pageable));
  }

  @GetMapping("/{userId}")
  public ResponseEntity<PageVO<UserRelationDto>> getFriends(
      @PathVariable(name = "userId") Long userId,
      @ParameterObject Pageable pageable,
      @RequestParam(name = "filter", required = false) String filter) {
    return ResponseEntity.ok(friendshipService.getFriends(userId, filter, pageable));
  }

  @GetMapping("/sent")
  public ResponseEntity<PageVO<UserRelationDto>> getSentRequests(
      @ParameterObject Pageable pageable,
      @RequestParam(name = "filter", required = false) String filter) {
    return ResponseEntity.ok(
        friendshipService.getSentRequests(SecurityUtils.getCurrentUserId(), filter, pageable));
  }

  @GetMapping("/pending")
  public ResponseEntity<PageVO<UserRelationDto>> getPendingRequests(
      @ParameterObject Pageable pageable,
      @RequestParam(name = "filter", required = false) String filter) {
    return ResponseEntity.ok(
        friendshipService.getPendingRequests(SecurityUtils.getCurrentUserId(), filter, pageable));
  }

  @GetMapping("/blocked")
  public ResponseEntity<PageVO<UserRelationDto>> getBlockedUsers(
      @ParameterObject Pageable pageable,
      @RequestParam(name = "filter", required = false) String filter) {
    return ResponseEntity.ok(
        friendshipService.getBlockedUsers(SecurityUtils.getCurrentUserId(), filter, pageable));
  }
}
