package com.socialnetwork.media_service.controller;

import com.socialnetwork.media_service.dto.react.ReactRequest;
import com.socialnetwork.media_service.dto.react.ReactResponse;
import com.socialnetwork.media_service.dto.react.ReactSummaryDto;
import com.socialnetwork.media_service.dto.react.ReactUserDto;
import com.socialnetwork.media_service.service.ReactService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.common.vo.TargetType;

@RestController
@RequestMapping("/api/v1/media/reacts")
@RequiredArgsConstructor
public class ReactController {

  private final ReactService reactService;

  @PostMapping("/toggle")
  public ResponseEntity<ReactResponse> toggle(@RequestBody ReactRequest req) {
    return ResponseEntity.ok(reactService.toggleReact(req));
  }

  @GetMapping("/{targetType}/{targetId}/users")
  public ResponseEntity<PageVO<ReactUserDto>> getReactUsers(
      @PathVariable("targetType") TargetType targetType,
      @PathVariable("targetId") Long targetId,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(reactService.getReactUsers(targetId, targetType, pageable));
  }

  @GetMapping("/{targetType}/{targetId}/summary")
  public ResponseEntity<ReactSummaryDto> getReactSummary(
      @PathVariable("targetType") TargetType targetType, @PathVariable("targetId") Long targetId) {
    Long currentUserId =
        Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    return ResponseEntity.ok(reactService.getReactSummary(targetId, targetType, currentUserId));
  }
}
