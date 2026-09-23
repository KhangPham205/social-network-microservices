package com.socialnetwork.media_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.media_service.service.RecommendationService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only maintenance endpoints; also covered by the {@code /admin/**} rule in SecurityConfig. */
@Slf4j
@RestController
@RequestMapping(ApiConstants.MEDIA + "/admin/recommendations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RecommendationAdminController {

  private final RecommendationService recommendationService;

  @PostMapping("/sync-posts")
  public ResponseEntity<Map<String, Integer>> syncExistingPosts() {
    log.info("Admin requested a full recommendation resync");
    return ResponseEntity.ok(Map.of("indexed", recommendationService.syncAllPostsFromDb()));
  }
}
