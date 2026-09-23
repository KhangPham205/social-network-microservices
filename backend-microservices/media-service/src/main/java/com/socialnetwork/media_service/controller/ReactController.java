package com.socialnetwork.media_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.media_service.dto.react.ReactRequest;
import com.socialnetwork.media_service.dto.react.ReactResponse;
import com.socialnetwork.media_service.dto.react.ReactSummaryDto;
import com.socialnetwork.media_service.dto.react.ReactUserDto;
import com.socialnetwork.media_service.service.ReactService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.MEDIA + "/reacts")
@RequiredArgsConstructor
public class ReactController {

  private final ReactService reactService;

  @PostMapping("/toggle")
  public ResponseEntity<ReactResponse> toggle(@RequestBody ReactRequest request) {
    return ResponseEntity.ok(reactService.toggleReact(request));
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
    return ResponseEntity.ok(reactService.getVisibleReactSummary(targetId, targetType));
  }
}
