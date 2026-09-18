package com.socialnetwork.media_service.controller;

import com.socialnetwork.media_service.service.RecommendationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

  private final RecommendationService recommendationService;

  @GetMapping("/explore")
  public ResponseEntity<List<Long>> getExploreFeed(
      @RequestParam Long currentUserId, @RequestParam(required = false) String filter) {
    return ResponseEntity.ok(recommendationService.getExploreFeed(currentUserId, filter));
  }
}
