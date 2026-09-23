package com.socialnetwork.chat_service.controller;

import com.socialnetwork.chat_service.dto.MessageRequest;
import com.socialnetwork.chat_service.dto.MessageResponse;
import com.socialnetwork.chat_service.service.MessageService;
import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.vo.CursorPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.MESSAGES)
@RequiredArgsConstructor
@Validated
public class MessageController {

  private final MessageService messageService;

  @PostMapping
  public ResponseEntity<MessageResponse> sendMessage(@Valid @RequestBody MessageRequest req) {
    return ResponseEntity.ok(messageService.sendMessage(req));
  }

  /** Newest page first; pass the previous {@code nextCursor} as {@code before} to go backwards. */
  @GetMapping("/{conversationId}/cursor")
  public ResponseEntity<CursorPage<MessageResponse>> getMessagesCursor(
      @PathVariable Long conversationId,
      @RequestParam(required = false) String before,
      @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
    return ResponseEntity.ok(messageService.getMessagesCursor(conversationId, before, limit));
  }

  @DeleteMapping("/{messageId}")
  public ResponseEntity<Void> deleteMessage(@PathVariable String messageId) {
    messageService.softDeleteMessage(messageId);
    return ResponseEntity.noContent().build();
  }
}
