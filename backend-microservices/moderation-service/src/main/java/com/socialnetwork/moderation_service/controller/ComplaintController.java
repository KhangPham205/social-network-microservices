package com.socialnetwork.moderation_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.moderation_service.dto.ComplaintResponse;
import com.socialnetwork.moderation_service.dto.CreateComplaintRequest;
import com.socialnetwork.moderation_service.enums.ComplaintStatus;
import com.socialnetwork.moderation_service.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.MODERATION + "/complaints")
@RequiredArgsConstructor
public class ComplaintController {

  private final ReportService reportService;

  /** Appealing against a moderation decision is open to every signed-in user. */
  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ComplaintResponse> createComplaint(
      @Valid @RequestBody CreateComplaintRequest request) {
    return ResponseEntity.ok(reportService.createComplaint(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('COMPLAINT:PROCESS')")
  public ResponseEntity<ComplaintResponse> updateComplaint(
      @PathVariable("id") Long id, @RequestParam ComplaintStatus status) {
    return ResponseEntity.ok(reportService.updateComplaint(id, status));
  }

  /**
   * Admin list, RSQL filtered, e.g. {@code ?filter=status=='PENDING';userId==7&page=0&size=10}.
   */
  @GetMapping
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('COMPLAINT:VIEW_ALL')")
  public ResponseEntity<PageVO<ComplaintResponse>> getComplaints(
      @RequestParam(required = false) String filter, @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(reportService.getComplaints(filter, pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('COMPLAINT:VIEW_ALL')")
  public ResponseEntity<ComplaintResponse> getComplaintById(@PathVariable("id") Long id) {
    return ResponseEntity.ok(reportService.getComplaintById(id));
  }
}
