package com.socialnetwork.moderation_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.security.SecurityUtils;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.moderation_service.dto.CreateReportRequest;
import com.socialnetwork.moderation_service.dto.ReportResponse;
import com.socialnetwork.moderation_service.dto.UpdateReportRequest;
import com.socialnetwork.moderation_service.service.ReportService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping(ApiConstants.MODERATION + "/reports")
@RequiredArgsConstructor
public class ReportController {

  private final ReportService reportService;

  /** Filing a report is open to every signed-in user. */
  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ReportResponse> createReport(
      @Valid @RequestBody CreateReportRequest request) {
    return ResponseEntity.ok(
        reportService.createReport(SecurityUtils.getCurrentUserId(), request));
  }

  @PutMapping
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('REPORT:PROCESS')")
  public ResponseEntity<List<ReportResponse>> updateReport(
      @Valid @RequestBody UpdateReportRequest request) {
    return ResponseEntity.ok(reportService.updateReport(request));
  }

  @GetMapping("/{reportId}")
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('REPORT:VIEW_ALL')")
  public ResponseEntity<ReportResponse> getReportById(@PathVariable("reportId") Long reportId) {
    return ResponseEntity.ok(reportService.getReportById(reportId));
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN') or hasAuthority('REPORT:VIEW_ALL')")
  public ResponseEntity<PageVO<ReportResponse>> getReports(
      @RequestParam(required = false) String filter, @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(reportService.getReports(filter, pageable));
  }
}
