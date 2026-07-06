package com.socialnetwork.moderation_service.controller;

import com.socialnetwork.moderation_service.dto.*;
import com.socialnetwork.moderation_service.enums.ComplaintStatus;
import com.socialnetwork.moderation_service.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vo.PageVO;

@RestController
@RequestMapping("/api/v1/moderation/complaints")
@RequiredArgsConstructor
public class ComplaintController {

  private final ReportService reportService;

  /** User tạo khiếu nại (Complaint) */
  @PostMapping
  @PreAuthorize("hasAuthority('COMPLAINT:CREATE')")
  public ResponseEntity<ComplaintResponse> createComplaint(
      @RequestBody CreateComplaintRequest request) {
    return ResponseEntity.ok(reportService.createComplaint(request));
  }

  @PutMapping("{id}")
  @PreAuthorize("hasAuthority('COMPLAINT:PROCESS')")
  public ResponseEntity<ComplaintResponse> updateComplaint(
      @PathVariable("id") Long id, @RequestParam ComplaintStatus status) {
    return ResponseEntity.ok(reportService.updateComplaint(id, status));
  }

  /**
   * Lấy danh sách khiếu nại (Complaints) Hỗ trợ filter: status, username, reportId... Ví dụ:
   * /api/v1/admin/complaints?filter=status=='PENDING';username=='khang'&page=0&size=10
   */
  @PreAuthorize("hasAuthority('COMPLAINT:VIEW_ALL')")
  @GetMapping
  public ResponseEntity<PageVO<ComplaintResponse>> getComplaints(
      @RequestParam(required = false) String filter, @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(reportService.getComplaints(filter, pageable));
  }

  /** Lấy chi tiết khiếu nại theo ID */
  @PreAuthorize("hasAuthority('COMPLAINT:VIEW_ALL')")
  @GetMapping("{id}")
  public ResponseEntity<ComplaintResponse> getComplaintById(@PathVariable("id") Long id) {
    return ResponseEntity.ok(reportService.getComplaintById(id));
  }
}
