package com.socialnetwork.chat_service.controller;

import com.socialnetwork.chat_service.dto.AddMembersRequest;
import com.socialnetwork.chat_service.dto.ConversationCreateRequest;
import com.socialnetwork.chat_service.dto.ConversationResponse;
import com.socialnetwork.chat_service.dto.ConversationSummaryResponse;
import com.socialnetwork.chat_service.dto.UpdateConversationRequest;
import com.socialnetwork.chat_service.dto.UpdateMemberRoleRequest;
import com.socialnetwork.chat_service.enums.ChatLabel;
import com.socialnetwork.chat_service.service.ConversationService;
import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.security.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.CONVERSATIONS)
@RequiredArgsConstructor
public class ConversationController {

  private final ConversationService conversationService;

  @PostMapping("/create")
  public ResponseEntity<ConversationResponse> create(
      @Valid @RequestBody ConversationCreateRequest req) {
    return ResponseEntity.ok(conversationService.createConversation(req));
  }

  @PutMapping("/update")
  public ResponseEntity<ConversationSummaryResponse> updateConversation(
      @Valid @RequestBody UpdateConversationRequest request) {
    return ResponseEntity.ok(
        conversationService.updateConversation(SecurityUtils.getCurrentUserId(), request));
  }

  @PostMapping("/addMembers")
  public ResponseEntity<ConversationSummaryResponse> addMembers(
      @Valid @RequestBody AddMembersRequest request) {
    return ResponseEntity.ok(
        conversationService.addMembersToGroup(SecurityUtils.getCurrentUserId(), request));
  }

  @PutMapping("/updateRoleMember")
  public ResponseEntity<ConversationSummaryResponse> updateMemberRole(
      @Valid @RequestBody UpdateMemberRoleRequest request) {
    return ResponseEntity.ok(
        conversationService.updateMemberRole(SecurityUtils.getCurrentUserId(), request));
  }

  @DeleteMapping("/{conversationId}/members/{userIdToRemove}")
  public ResponseEntity<ConversationSummaryResponse> removeMember(
      @PathVariable Long conversationId, @PathVariable Long userIdToRemove) {
    return ResponseEntity.ok(
        conversationService.removeMemberFromGroup(
            SecurityUtils.getCurrentUserId(), conversationId, userIdToRemove));
  }

  @DeleteMapping("/{conversationId}/leave")
  public ResponseEntity<Void> leaveConversation(@PathVariable Long conversationId) {
    conversationService.leaveConversation(SecurityUtils.getCurrentUserId(), conversationId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public ResponseEntity<List<ConversationSummaryResponse>> myConversations() {
    return ResponseEntity.ok(
        conversationService.getUserConversations(SecurityUtils.getCurrentUserId()));
  }

  @GetMapping("/{conversationId}")
  public ResponseEntity<ConversationSummaryResponse> getConversationById(
      @PathVariable Long conversationId) {
    return ResponseEntity.ok(
        conversationService.getConversationById(SecurityUtils.getCurrentUserId(), conversationId));
  }

  @PostMapping("/{roomId}/labels")
  public ResponseEntity<Void> addLabel(
      @PathVariable Long roomId, @RequestParam ChatLabel label) {
    conversationService.addLabelToConversation(SecurityUtils.getCurrentUserId(), roomId, label);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{roomId}/labels")
  public ResponseEntity<Void> removeLabel(
      @PathVariable Long roomId, @RequestParam ChatLabel label) {
    conversationService.removeLabelFromConversation(
        SecurityUtils.getCurrentUserId(), roomId, label);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/labels/all")
  public ResponseEntity<ChatLabel[]> getAllAvailableLabels() {
    return ResponseEntity.ok(ChatLabel.values());
  }
}
