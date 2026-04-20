// ConversationController.java (get my conversations)
package com.socialnetwork.chat_service.controller;

import com.socialnetwork.chat_service.dto.*;
import com.socialnetwork.chat_service.service.ConversationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import utils.SecurityUtils;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

  private final ConversationService conversationService;

  @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ConversationResponse> create(
      @ModelAttribute ConversationCreateRequest req) {
    return ResponseEntity.ok(conversationService.createConversation(req));
  }

  @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ConversationSummaryResponse> updateConversation(
      @ModelAttribute UpdateConversationRequest request) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(conversationService.updateConversation(currentUserId, request));
  }

  @PostMapping("/addMembers")
  public ResponseEntity<ConversationSummaryResponse> addMembers(
      @RequestBody AddMembersRequest request) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(conversationService.addMembersToGroup(currentUserId, request));
  }

  @PutMapping("/updateRoleMember")
  public ResponseEntity<ConversationSummaryResponse> updateMemberRole(
      @RequestBody UpdateMemberRoleRequest request) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(conversationService.updateMemberRole(currentUserId, request));
  }

  @DeleteMapping("/{conversationId}/members/{userIdToRemove}")
  public ResponseEntity<ConversationSummaryResponse> removeMember(
      @PathVariable Long conversationId, @PathVariable Long userIdToRemove) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(
        conversationService.removeMemberFromGroup(currentUserId, conversationId, userIdToRemove));
  }

  @DeleteMapping("/{conversationId}/leave")
  public ResponseEntity<Void> leaveConversation(@PathVariable Long conversationId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    conversationService.leaveConversation(currentUserId, conversationId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public ResponseEntity<List<ConversationSummaryResponse>> myConversations() {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(conversationService.getUserConversations(currentUserId));
  }

  @GetMapping("/{conversationId}")
  public ResponseEntity<ConversationSummaryResponse> getConversationById(
      @PathVariable Long conversationId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return ResponseEntity.ok(
        conversationService.getConversationById(currentUserId, conversationId));
  }
}
