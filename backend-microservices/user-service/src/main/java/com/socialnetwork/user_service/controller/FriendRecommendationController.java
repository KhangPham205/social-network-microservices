package com.socialnetwork.user_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.user_service.dto.UserRelationDto;
import com.socialnetwork.user_service.service.FriendRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.USERS)
@RequiredArgsConstructor
public class FriendRecommendationController {

  private final FriendRecommendationService friendRecommendationService;

  @GetMapping("/{userId}/recommendations")
  public ResponseEntity<PageVO<UserRelationDto>> getFriendRecommendations(
      @PathVariable("userId") Long userId, @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(friendRecommendationService.getFriendRecommendations(userId, pageable));
  }
}
