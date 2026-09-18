package com.socialnetwork.moderation_service.service;

import com.socialnetwork.moderation_service.dto.*;
import com.socialnetwork.moderation_service.enums.ComplaintStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.common.vo.TargetType;

public interface ReportService {
  // Report
  ReportResponse createReport(Long reporterId, CreateReportRequest request);

  List<ReportResponse> updateReport(UpdateReportRequest request);

  ReportResponse getReportById(Long reportId);

  PageVO<ReportResponse> getReports(String filter, Pageable pageable);

  // Complaint
  ComplaintResponse createComplaint(CreateComplaintRequest request);

  ComplaintResponse updateComplaint(Long id, ComplaintStatus status);

  PageVO<ComplaintResponse> getComplaints(String filter, Pageable pageable);

  ComplaintResponse getComplaintById(Long id);

  PageVO<ReportResponse> getReportsByContent(
      String targetId, TargetType targetType, Pageable pageable);

  PageVO<ComplaintResponse> getComplaintsByContent(
      String targetId, TargetType targetType, Pageable pageable);
}
