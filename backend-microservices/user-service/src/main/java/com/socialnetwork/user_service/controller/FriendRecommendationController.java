package com.socialnetwork.user_service.controller;

import com.socialnetwork.user_service.dto.UserRelationDto;
import com.socialnetwork.user_service.service.FriendRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vo.PageVO;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class FriendRecommendationController {

  private final FriendRecommendationService friendRecommendationService;

  @GetMapping("/{userId}/recommendations")
  public ResponseEntity<PageVO<UserRelationDto>> getFriendRecommendations(
      @PathVariable Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {

    Pageable pageable = PageRequest.of(page, size);
    PageVO<UserRelationDto> recommendations =
        friendRecommendationService.getFriendRecommendations(userId, pageable);

    return ResponseEntity.ok(recommendations);
  }
}
