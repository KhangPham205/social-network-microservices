package com.socialnetwork.moderation_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.vo.AccountStatus;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.dto.ComplaintResponse;
import com.socialnetwork.moderation_service.dto.ModerationLogResponse;
import com.socialnetwork.moderation_service.dto.ModerationMessageResponse;
import com.socialnetwork.moderation_service.dto.ModerationUserDetailResponse;
import com.socialnetwork.moderation_service.dto.ReportResponse;
import com.socialnetwork.moderation_service.dto.UserModerationResponse;
import com.socialnetwork.moderation_service.dto.external.CommentResponse;
import com.socialnetwork.moderation_service.dto.external.PostResponse;
import com.socialnetwork.moderation_service.service.ModerationService;
import com.socialnetwork.moderation_service.service.ReportService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative views and actions. Every method is guarded by {@code @PreAuthorize}: an
 * administrator, or the holder of the matching fine-grained permission, may call it.
 */
@RestController
@RequestMapping(ApiConstants.MODERATION)
@RequiredArgsConstructor
public class ModerationController {

  private final ReportService reportService;
  private final ModerationService moderationService;

  // ── Composition views ────────────────────────────────────────────────────

  @GetMapping("/users/{id}")
  @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('USER:READ_SENSITIVE', 'MODERATION:ACCESS')")
  public ResponseEntity<ModerationUserDetailResponse> getUserDetail(@PathVariable("id") Long id) {
    return ResponseEntity.ok(moderationService.getUserDetailForAdmin(id));
  }

  @GetMapping("/users/{id}/violations")
  @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('USER:READ_SENSITIVE', 'MODERATION:ACCESS')")
  public ResponseEntity<PageVO<ReportResponse>> getUserViolations(
      @PathVariable("id") Long id, @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(moderationService.getUserViolations(id, pageable));
  }

  @GetMapping("/users")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<UserModerationResponse>> getUsersWithReports(
      @RequestParam(required = false) String filter, @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(moderationService.getUsersWithReportCount(pageable, filter));
  }

  @GetMapping("/posts/{id}")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PostResponse> getPostDetail(@PathVariable("id") Long id) {
    return ResponseEntity.ok(moderationService.getPostDetailForAdmin(id));
  }

  @GetMapping("/comments/{id}")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<CommentResponse> getCommentDetail(@PathVariable("id") Long id) {
    return ResponseEntity.ok(moderationService.getCommentDetailForAdmin(id));
  }

  @GetMapping("/messages/{id}")
  @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('MESSAGE:READ_ANY', 'MODERATION:ACCESS')")
  public ResponseEntity<ModerationMessageResponse> getMessageDetail(@PathVariable("id") String id) {
    return ResponseEntity.ok(moderationService.getMessageDetailForAdmin(id));
  }

  @GetMapping("/posts/flagged")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<PostResponse>> getFlaggedPosts(
      @RequestParam(value = "filter", required = false) String filter,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(moderationService.getFlaggedPosts(filter, pageable));
  }

  @GetMapping("/comments/flagged")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<CommentResponse>> getFlaggedComments(
      @RequestParam(value = "filter", required = false) String filter,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(moderationService.getFlaggedComments(filter, pageable));
  }

  @GetMapping("/messages/flagged")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<ModerationMessageResponse>> getFlaggedMessages(
      @RequestParam(value = "filter", required = false) String filter,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(moderationService.getFlaggedMessages(filter, pageable));
  }

  @GetMapping("/history")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<ModerationLogResponse>> getModerationLogs(
      @RequestParam(required = false) String filter, @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(moderationService.getModerationLogs(filter, pageable));
  }

  @GetMapping("/{type}/{id}/history")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<ModerationLogResponse>> getModerationHistory(
      @PathVariable("type") TargetType type,
      @PathVariable("id") String id,
      @ParameterObject Pageable pageable,
      @RequestParam(required = false) String filter) {
    return ResponseEntity.ok(moderationService.getHistory(type, id, pageable, filter));
  }

  @GetMapping("/{type}/{id}/reports")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<ReportResponse>> getContentReports(
      @PathVariable("type") TargetType type,
      @PathVariable("id") String id,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(reportService.getReportsByContent(id, type, pageable));
  }

  @GetMapping("/{type}/{id}/complaints")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('MODERATION:ACCESS')")
  public ResponseEntity<PageVO<ComplaintResponse>> getContentComplaints(
      @PathVariable("type") TargetType type,
      @PathVariable("id") String id,
      @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(reportService.getComplaintsByContent(id, type, pageable));
  }

  // ── Actions ──────────────────────────────────────────────────────────────

  @PutMapping("/users/{id}/block")
  @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('USER:BLOCK', 'MODERATION:ACCESS')")
  public ResponseEntity<Map<String, String>> blockUser(
      @PathVariable("id") Long id, @RequestBody(required = false) Map<String, String> request) {
    moderationService.updateUserStatus(id, AccountStatus.BLOCKED, reasonOf(request));
    return ResponseEntity.ok(Map.of("message", "User blocked successfully"));
  }

  @PutMapping("/users/{id}/unblock")
  @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('USER:BLOCK', 'MODERATION:ACCESS')")
  public ResponseEntity<Map<String, String>> unblockUser(
      @PathVariable("id") Long id, @RequestBody(required = false) Map<String, String> request) {
    moderationService.updateUserStatus(id, AccountStatus.ACTIVE, reasonOf(request));
    return ResponseEntity.ok(Map.of("message", "User unblocked successfully"));
  }

  @PutMapping("/{type}/{id}/block")
  @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('POST:DELETE_ANY', 'MODERATION:ACCESS')")
  public ResponseEntity<Map<String, String>> blockContent(
      @PathVariable("type") TargetType type, @PathVariable("id") String id) {
    moderationService.blockContent(id, type, null);
    return ResponseEntity.ok(Map.of("message", "Content blocked successfully"));
  }

  @PutMapping("/{type}/{id}/unblock")
  @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('POST:DELETE_ANY', 'MODERATION:ACCESS')")
  public ResponseEntity<Map<String, String>> unblockContent(
      @PathVariable("type") TargetType type, @PathVariable("id") String id) {
    moderationService.unblockContent(id, type, null);
    return ResponseEntity.ok(Map.of("message", "Content unblocked successfully"));
  }

  private static String reasonOf(Map<String, String> request) {
    return request == null ? null : request.get("reason");
  }
}
