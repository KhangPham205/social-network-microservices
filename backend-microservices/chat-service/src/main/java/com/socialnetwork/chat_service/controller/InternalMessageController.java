package com.socialnetwork.chat_service.controller;

import com.socialnetwork.chat_service.service.MessageService;
import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.dto.MessageModerationView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API consumed by moderation-service. Reachable only with a valid {@code
 * X-Internal-Token}; the gateway 404s any path containing {@code /internal/}.
 */
@RestController
@RequestMapping(ApiConstants.CHAT + ApiConstants.INTERNAL + "/messages")
@RequiredArgsConstructor
public class InternalMessageController {

  private final MessageService messageService;

  @GetMapping("/{id}")
  public ResponseEntity<MessageModerationView> getMessage(@PathVariable String id) {
    return ResponseEntity.ok(messageService.getModerationView(id));
  }

  @GetMapping("/{id}/owner-id")
  public ResponseEntity<Long> getMessageOwnerId(@PathVariable String id) {
    return ResponseEntity.ok(messageService.getMessageOwnerId(id));
  }

  @PostMapping("/batch")
  public ResponseEntity<List<MessageModerationView>> getMessagesByIds(
      @RequestBody List<String> ids) {
    return ResponseEntity.ok(messageService.getModerationViews(ids));
  }
}
