package com.socialnetwork.user_service.controller;

import com.socialnetwork.user_service.dto.UserModerationDto;
import com.socialnetwork.user_service.dto.UserProfileDto;
import com.socialnetwork.user_service.service.FriendshipService;
import com.socialnetwork.user_service.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/internal")
@RequiredArgsConstructor
public class InternalUserController {

  private final UserService userService;
  private final FriendshipService friendshipService;

  // API này không cần token (đã được cấu hình permitAll trong SecurityConfig)
  @PostMapping("/create")
  public ResponseEntity<Void> createEmptyProfile(
      @RequestParam("accountId") Long accountId, @RequestParam("displayName") String displayName) {

    userService.createDefaultProfile(accountId, displayName);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/{userId}")
  public ResponseEntity<UserProfileDto> getUserProfileInternal(
      @PathVariable("userId") Long userId) {
    return ResponseEntity.ok(userService.getProfile(userId));
  }

  // Lấy danh sách ID của tất cả bạn bè + những người đang follow
  @GetMapping("/{userId}/network-ids")
  public ResponseEntity<List<Long>> getNetworkIds(@PathVariable("userId") Long userId) {
    List<Long> networkIds = friendshipService.getNetworkIds(userId);
    return ResponseEntity.ok(networkIds);
  }

  // Kiểm tra xem user1 có phải bạn của user2 không
  @GetMapping("/check-friendship")
  public ResponseEntity<Boolean> isFriend(
      @RequestParam("user1") Long user1, @RequestParam("user2") Long user2) {
    boolean isFriend = friendshipService.isFriend(user1, user2);
    return ResponseEntity.ok(isFriend);
  }

  // ================= API NỘI BỘ CHO MODERATION SERVICE =================

  @GetMapping("/{id}/admin-detail")
  public ResponseEntity<UserModerationDto> getUserDetailForAdmin(@PathVariable("id") Long id) {
    return ResponseEntity.ok(userService.getUserForModeration(id));
  }

  @PostMapping("/batch")
  public ResponseEntity<List<UserModerationDto>> getUsersByIds(@RequestBody List<Long> ids) {
    return ResponseEntity.ok(userService.getUsersByIds(ids));
  }
}
