package com.socialnetwork.moderation_service.service;

import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.dto.ComplaintResponse;
import com.socialnetwork.moderation_service.dto.CreateComplaintRequest;
import com.socialnetwork.moderation_service.dto.CreateReportRequest;
import com.socialnetwork.moderation_service.dto.ReportResponse;
import com.socialnetwork.moderation_service.dto.UpdateReportRequest;
import com.socialnetwork.moderation_service.enums.ComplaintStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;

/** Reports filed against content and complaints filed by the owners of hidden content. */
public interface ReportService {

  // ── Reports ────────────────────────────────────────────────────────────

  ReportResponse createReport(Long reporterId, CreateReportRequest request);

  /** Approving a report hides the reported content. */
  List<ReportResponse> updateReport(UpdateReportRequest request);

  ReportResponse getReportById(Long reportId);

  PageVO<ReportResponse> getReports(String filter, Pageable pageable);

  PageVO<ReportResponse> getReportsByContent(
      String targetId, TargetType targetType, Pageable pageable);

  // ── Complaints ─────────────────────────────────────────────────────────

  ComplaintResponse createComplaint(CreateComplaintRequest request);

  /** Approving a complaint restores the content it is about. */
  ComplaintResponse updateComplaint(Long id, ComplaintStatus status);

  PageVO<ComplaintResponse> getComplaints(String filter, Pageable pageable);

  ComplaintResponse getComplaintById(Long id);

  PageVO<ComplaintResponse> getComplaintsByContent(
      String targetId, TargetType targetType, Pageable pageable);
}
