package com.socialnetwork.moderation_service.controller;

import com.socialnetwork.moderation_service.dto.*;
import com.socialnetwork.moderation_service.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vo.PageVO;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // 1. User tạo report
    @PostMapping
    @PreAuthorize("hasAuthority('REPORT:CREATE')")
    public ResponseEntity<ReportResponse> createReport(@RequestBody CreateReportRequest request) {
        Long currentUserId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        return ResponseEntity.ok(reportService.createReport(currentUserId, request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('REPORT:PROCESS')")
    public ResponseEntity<List<ReportResponse>> updateReport(@RequestBody UpdateReportRequest request) {
        return ResponseEntity.ok(reportService.updateReport(request));
    }

    @GetMapping("/{reportId}")
    @PreAuthorize("hasAuthority('REPORT:VIEW_ALL')")
    public ResponseEntity<ReportResponse> getReportById(@PathVariable Long reportId) {
        return ResponseEntity.ok(reportService.getReportById(reportId));
    }

    // 2. Admin xem danh sách report (có filter)
    @GetMapping
    @PreAuthorize("hasAuthority('REPORT:VIEW_ALL')")
    public ResponseEntity<PageVO<ReportResponse>> getReports(
            @RequestParam(required = false) String filter,
            @ParameterObject Pageable pageable
    ) {
        return ResponseEntity.ok(reportService.getReports(filter, pageable));
    }
}