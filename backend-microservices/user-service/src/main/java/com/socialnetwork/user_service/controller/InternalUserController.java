package com.socialnetwork.user_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.dto.UserSummary;
import com.socialnetwork.user_service.dto.UserModerationDto;
import com.socialnetwork.user_service.dto.UserProfileDto;
import com.socialnetwork.user_service.service.FriendshipService;
import com.socialnetwork.user_service.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API. Reachable only with the {@code X-Internal-Token} header (rule added by
 * {@code JwtSecurityConfigurer}); the gateway 404s every path containing {@code /internal/}.
 */
@RestController
@RequestMapping(ApiConstants.USERS + ApiConstants.INTERNAL)
@RequiredArgsConstructor
public class InternalUserController {

  private final UserService userService;
  private final FriendshipService friendshipService;

  /** Registration saga: auth-service asks for the profile of a new account. Idempotent. */
  @PostMapping("/create")
  public ResponseEntity<UserSummary> createEmptyProfile(
      @RequestParam("accountId") Long accountId, @RequestParam("displayName") String displayName) {
    UserSummary summary = userService.createDefaultProfile(accountId, displayName);
    return ResponseEntity.status(HttpStatus.CREATED).body(summary);
  }

  @GetMapping("/{userId}")
  public ResponseEntity<UserProfileDto> getUserProfile(@PathVariable("userId") Long userId) {
    return ResponseEntity.ok(userService.getProfile(userId));
  }

  /** Display name and avatar of many users at once, for the read-model caches. */
  @PostMapping("/summaries")
  public ResponseEntity<List<UserSummary>> getSummaries(@RequestBody List<Long> ids) {
    return ResponseEntity.ok(userService.getSummaries(ids));
  }

  /** Friends plus followed users, used by media-service to build a feed. */
  @GetMapping("/{userId}/network-ids")
  public ResponseEntity<List<Long>> getNetworkIds(@PathVariable("userId") Long userId) {
    return ResponseEntity.ok(friendshipService.getNetworkIds(userId));
  }

  @GetMapping("/check-friendship")
  public ResponseEntity<Boolean> isFriend(
      @RequestParam("user1") Long user1, @RequestParam("user2") Long user2) {
    return ResponseEntity.ok(friendshipService.isFriend(user1, user2));
  }

  // moderation-service views

  @GetMapping("/{userId}/admin-detail")
  public ResponseEntity<UserModerationDto> getUserDetailForAdmin(
      @PathVariable("userId") Long userId) {
    return ResponseEntity.ok(userService.getUserForModeration(userId));
  }

  @PostMapping("/batch")
  public ResponseEntity<List<UserModerationDto>> getUsersByIds(@RequestBody List<Long> ids) {
    return ResponseEntity.ok(userService.getUsersByIds(ids));
  }
}
