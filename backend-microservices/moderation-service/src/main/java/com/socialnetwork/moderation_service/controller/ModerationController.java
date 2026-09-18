package com.socialnetwork.moderation_service.controller;

import com.socialnetwork.moderation_service.dto.*;
import com.socialnetwork.moderation_service.dto.external.CommentResponse;
import com.socialnetwork.moderation_service.dto.external.PostResponse;
import com.socialnetwork.moderation_service.enums.AccountStatus;
import com.socialnetwork.moderation_service.service.ModerationService;
import com.socialnetwork.moderation_service.service.ReportService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.common.vo.TargetType;

@RestController
@RequestMapping("/api/v1/moderation")
@RequiredArgsConstructor
public class ModerationController {

  private final ReportService reportService;
  private final ModerationService moderationService;

  @GetMapping("/users/{id}")
  @PreAuthorize("hasAnyAuthority('USER:READ_SENSITIVE', 'MODERATION:ACCESS')")
  public ResponseEntity<ModerationUserDetailResponse> getUserDetail(@PathVariable("id") Long id) {
    return ResponseEntity.ok(moderationService.getUserDetailForAdmin(id));
  }

  @GetMapping("/posts/{id}")
  @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PostResponse> getPostDetail(@PathVariable("id") Long id) {
    return ResponseEntity.ok(moderationService.getPostDetailForAdmin(id));
  }

  @GetMapping("/comments/{id}")
  @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<CommentResponse> getCommentDetail(@PathVariable("id") Long id) {
    return ResponseEntity.ok(moderationService.getCommentDetailForAdmin(id));
  }

  @GetMapping("/messages/{id}")
  @PreAuthorize("hasAnyAuthority('MESSAGE:READ_ANY', 'MODERATION:ACCESS')")
  public ResponseEntity<ModerationMessageResponse> getMessageDetail(@PathVariable("id") String id) {
    return ResponseEntity.ok(moderationService.getMessageDetailForAdmin(id));
  }

  @GetMapping("/users/{id}/violations")
  @PreAuthorize("hasAnyAuthority('USER:READ_SENSITIVE', 'MODERATION:ACCESS')")
  public ResponseEntity<PageVO<ReportResponse>> getUserViolations(
      @PathVariable("id") Long id, @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(moderationService.getUserViolations(id, pageable));
  }

  @GetMapping("/users")
  @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<UserModerationResponse>> getUsersWithReports(
      @RequestParam(required = false) String filter, @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(moderationService.getUsersWithReportCount(pageable, filter));
  }

  @GetMapping("/history")
  @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<ModerationLogResponse>> getModerationLogs(
      @RequestParam(required = false) String filter, @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(moderationService.getModerationLogs(filter, pageable));
  }

  @GetMapping("/{type}/{id}/history")
  @PreAuthorize("hasAnyAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<ModerationLogResponse>> getModerationHistory(
      @PathVariable("type") TargetType type,
      @PathVariable("id") String id,
      @ParameterObject Pageable pageable,
      @RequestParam(required = false) String filter) {
    return ResponseEntity.ok(moderationService.getHistory(type, id, pageable, filter));
  }

  @GetMapping("/posts/flagged")
  @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<PostResponse>> getFlaggedPosts(
      @RequestParam(value = "filter", required = false) String filter,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(moderationService.getFlaggedPosts(filter, pageable));
  }

  @GetMapping("/comments/flagged")
  @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<CommentResponse>> getFlaggedComments(
      @RequestParam(value = "filter", required = false) String filter,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(moderationService.getFlaggedComments(filter, pageable));
  }

  @GetMapping("/messages/flagged")
  @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<ModerationMessageResponse>> getFlaggedMessages(
      @RequestParam(value = "filter", required = false) String filter,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(moderationService.getFlaggedMessages(filter, pageable));
  }

  @GetMapping("/messages/flagged/grouped")
  public ResponseEntity<PageVO<GroupedFlaggedMessageResponse>> getGroupedFlaggedMessages(
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(moderationService.getGroupedFlaggedMessages(pageable));
  }

  @GetMapping("/{type}/{id}/reports")
  @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<ReportResponse>> getContentReports(
      @PathVariable("type") TargetType type,
      @PathVariable("id") String id,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(reportService.getReportsByContent(id, type, pageable));
  }

  @GetMapping("/{type}/{id}/complaints")
  @PreAuthorize("hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<ComplaintResponse>> getContentComplaints(
      @PathVariable("type") TargetType type,
      @PathVariable("id") String id,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(reportService.getComplaintsByContent(id, type, pageable));
  }

  @PutMapping("/users/{id}/block")
  @PreAuthorize("hasAnyAuthority('USER:BLOCK', 'MODERATION:ACCESS')")
  public ResponseEntity<Map<String, String>> blockUser(
      @PathVariable("id") Long id, @RequestBody(required = false) Map<String, String> request) {
    String reason = (request != null) ? request.get("reason") : "";
    moderationService.updateUserStatus(id, AccountStatus.BLOCKED, reason);
    return ResponseEntity.ok(
        Map.of(
            "message", "User blocked successfully",
            "statusCode", "200"));
  }

  @PutMapping("/users/{id}/unblock")
  @PreAuthorize("hasAnyAuthority('USER:BLOCK', 'MODERATION:ACCESS')")
  public ResponseEntity<Map<String, String>> unblockUser(
      @PathVariable("id") Long id, @RequestBody(required = false) Map<String, String> request) {
    String reason = (request != null) ? request.get("reason") : "";
    moderationService.updateUserStatus(id, AccountStatus.ACTIVE, reason);
    return ResponseEntity.ok(
        Map.of(
            "message", "User unblocked successfully",
            "statusCode", "200"));
  }

  @PutMapping("/{type}/{id}/block")
  @PreAuthorize("hasAnyAuthority('POST:DELETE_ANY', 'MODERATION:ACCESS')")
  public ResponseEntity<Map<String, String>> blockContent(
      @PathVariable TargetType type, @PathVariable String id) {
    moderationService.blockContent(id, type);
    return ResponseEntity.ok(
        Map.of(
            "message", "Content blocked successfully",
            "statusCode", "200"));
  }

  @PutMapping("/{type}/{id}/unblock")
  @PreAuthorize("hasAnyAuthority('POST:DELETE_ANY', 'MODERATION:ACCESS')")
  public ResponseEntity<Map<String, String>> unblockContent(
      @PathVariable TargetType type, @PathVariable Long id) {
    moderationService.unblockContent(id, type);
    return ResponseEntity.ok(
        Map.of(
            "message", "Content unblocked successfully",
            "statusCode", "200"));
  }
}
