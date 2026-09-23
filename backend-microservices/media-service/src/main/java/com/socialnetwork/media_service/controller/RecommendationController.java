package com.socialnetwork.media_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.security.SecurityUtils;
import com.socialnetwork.media_service.service.RecommendationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.MEDIA + "/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

  private final RecommendationService recommendationService;

  /** Ranked post ids for the caller; the user always comes from the token, never from a param. */
  @GetMapping("/explore")
  public ResponseEntity<List<Long>> getExploreFeed(
      @RequestParam(value = "filter", required = false) String filter) {
    return ResponseEntity.ok(
        recommendationService.getExploreFeed(SecurityUtils.getCurrentUserId(), filter));
  }
}
