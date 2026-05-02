// MessageController.java
package com.socialnetwork.chat_service.controller;

import com.socialnetwork.chat_service.dto.MessageRequest;
import com.socialnetwork.chat_service.dto.MessageResponse;
import com.socialnetwork.chat_service.service.MessageService;
import constants.ApiConstants;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vo.CursorPage;

@RestController
@RequestMapping(ApiConstants.MESSAGES)
@RequiredArgsConstructor
public class MessageController {

  private final MessageService messageService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> sendMessage(@ModelAttribute MessageRequest req) {

    // if (req.getMediaFiles() != null) {
    //     log.info("Received {} files", req.getMediaFiles().size());
    // }

    Map<String, Object> saved = messageService.sendMessage(req);
    return ResponseEntity.ok(saved);
  }

  // Cursor paging: newest page: GET /api/v1/messages/{conversationId}?limit=30
  // older page: add ?before=<messageId>
  @GetMapping("/{conversationId}/cursor")
  public ResponseEntity<CursorPage<MessageResponse>> getMessagesCursor(
      @PathVariable Long conversationId,
      @RequestParam(required = false) String before,
      @RequestParam(defaultValue = "30") int limit) {
    CursorPage<MessageResponse> page =
        messageService.getMessagesCursor(conversationId, before, limit);
    return ResponseEntity.ok(page);
  }

  // convenience endpoint to fetch all messages (careful)
  @GetMapping("/{conversationId}/all")
  public ResponseEntity<List<Map<String, Object>>> getAllMessages(
      @PathVariable Long conversationId) {
    return ResponseEntity.ok(messageService.getMessages(conversationId));
  }
}
