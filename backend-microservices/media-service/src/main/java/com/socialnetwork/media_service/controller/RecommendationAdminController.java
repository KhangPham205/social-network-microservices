package com.socialnetwork.media_service.controller;

import com.socialnetwork.media_service.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/media/recommendations/admin")
@RequiredArgsConstructor
public class RecommendationAdminController {

  private final RecommendationService recommendationService;

  @PostMapping("/sync-posts")
  public ResponseEntity<String> syncExistingPosts() {
    log.info("Received request to sync all posts from database");
    recommendationService.syncAllPostsFromDb();
    return ResponseEntity.ok("Sync initiated and completed successfully!");
  }
}
