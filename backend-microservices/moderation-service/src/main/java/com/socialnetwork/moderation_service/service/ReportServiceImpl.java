package com.socialnetwork.moderation_service.service;

import com.socialnetwork.moderation_service.client.ChatClient;
import com.socialnetwork.moderation_service.client.MediaClient;
import com.socialnetwork.moderation_service.client.UserClient;
import com.socialnetwork.moderation_service.dto.*;
import com.socialnetwork.moderation_service.dto.external.UserExternalDto;
import com.socialnetwork.moderation_service.enums.ComplaintStatus;
import com.socialnetwork.moderation_service.enums.ReportStatus;
import com.socialnetwork.moderation_service.mapper.ReportMapper;
import com.socialnetwork.moderation_service.model.Complaint;
import com.socialnetwork.moderation_service.model.Report;
import com.socialnetwork.moderation_service.repository.ComplaintRepository;
import com.socialnetwork.moderation_service.repository.ReportRepository;
import exception.BadRequestException;
import exception.ResourceNotFoundException;
import io.github.perplexhub.rsql.RSQLJPASupport;

import java.util.*;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vo.PageVO;
import vo.TargetType;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

  private final ReportRepository reportRepository;
  private final ComplaintRepository complaintRepository;
  private final ReportMapper reportMapper;

  private final UserClient userClient;
  private final MediaClient mediaClient;
  private final ChatClient chatClient;

  @Override
  @Transactional
  public ReportResponse createReport(Long reporterId, CreateReportRequest request) {
    if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
        reporterId, request.getTargetType(), request.getTargetId())) {
      throw new BadRequestException("You have already reported this content.");
    }

    Long targetOwnerId = determineTargetOwnerId(request.getTargetType(), request.getTargetId());
    if (targetOwnerId == null) {
      throw new BadRequestException("Unable to determine content owner. Please try again later.");
    }

    Report report =
        Report.builder()
            .reporterId(reporterId)
            .targetType(request.getTargetType())
            .targetId(request.getTargetId())
            .targetUserId(targetOwnerId)
            .reason(request.getReason())
            .customReason(request.getReason().name().equals("OTHER") ? request.getCustomReason() : null)
            .status(ReportStatus.PENDING)
            .isBannedBySystem(false)
            .build();

    return reportMapper.toResponse(reportRepository.save(report));
  }

  /** Lấy ID của chủ bài viết/comment/tin nhắn thông qua HTTP Client */
  private Long determineTargetOwnerId(TargetType targetType, String targetId) {
    try {
      return switch (targetType) {
        case POST -> mediaClient.getPostOwnerId(targetId);
        case COMMENT -> mediaClient.getCommentOwnerId(targetId);
        case USER -> Long.valueOf(targetId);
        case MESSAGE -> chatClient.getMessageOwnerId(targetId);
        default -> null;
      };
    } catch (Exception e) {
      log.error("Lỗi khi gọi service lấy owner cho {}: {}", targetType, e.getMessage());
      return null;
    }
  }

  @Override
  @Transactional
  public List<ReportResponse> updateReport(UpdateReportRequest request) {
    List<Report> reports = reportRepository.findAllById(request.getReportIds());

    if (reports.isEmpty()) {
      throw new ResourceNotFoundException("No reports found for the provided IDs");
    }

    if (request.getReportStatus() == null) {
      throw new BadRequestException("Report status must be provided for update");
    }

    for (Report report : reports) {
      report.setStatus(request.getReportStatus());
    }

    List<Report> updatedReports = reportRepository.saveAll(reports);

    return updatedReports.stream().map(reportMapper::toResponse).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ReportResponse getReportById(Long reportId) {
    Report report =
        reportRepository
            .findById(reportId)
            .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
    return reportMapper.toResponse(report);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ReportResponse> getReports(String filter, Pageable pageable) {
    Specification<Report> spec = Specification.where((Specification<Report>) null);

    if (filter != null && !filter.isBlank()) {
      Map<String, String> propertyPathMapper = new HashMap<>();
      propertyPathMapper.put("id", "id");
      propertyPathMapper.put("targetType", "targetType");
      propertyPathMapper.put("reason", "reason");
      propertyPathMapper.put("reporterId", "reporterId");
      propertyPathMapper.put("targetUserId", "targetUserId");

      spec = RSQLJPASupport.toSpecification(filter, propertyPathMapper);
    }

    Page<Report> page = reportRepository.findAll(spec, pageable);

    List<ReportResponse> content =
        page.getContent().stream().map(reportMapper::toResponse).toList();

    return PageVO.<ReportResponse>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(content.size())
        .content(content)
        .build();
  }

  // --- COMPLAINT LOGIC ---

  @Override
  @Transactional(readOnly = true)
  public PageVO<ComplaintResponse> getComplaints(String filter, Pageable pageable) {
    Specification<Complaint> spec = Specification.where((Specification<Complaint>) null);

    if (filter != null && !filter.isBlank()) {
      Map<String, String> propertyPathMapper = new HashMap<>();
      propertyPathMapper.put("userId", "userId");
      propertyPathMapper.put("targetType", "targetType");
      propertyPathMapper.put("status", "status");

      spec = RSQLJPASupport.toSpecification(filter, propertyPathMapper);
    }

    Page<Complaint> page = complaintRepository.findAll(spec, pageable);

    List<ComplaintResponse> content =
        page.getContent().stream().map(reportMapper::toResponse).toList();

    return PageVO.<ComplaintResponse>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(content.size())
        .content(content)
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public ComplaintResponse getComplaintById(Long id) {
    Complaint complaint =
        complaintRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));
    return reportMapper.toResponse(complaint);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ReportResponse> getReportsByContent(
      String targetId, TargetType targetType, Pageable pageable) {

    Page<Report> page = reportRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable);
    if (page.isEmpty()) return buildEmptyPageVO(page);

    List<Long> reporterIds = page.getContent().stream()
        .map(Report::getReporterId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();

    Map<Long, UserExternalDto> userMap = new HashMap<>();
    if (!reporterIds.isEmpty()) {
      try {
        List<UserExternalDto> users = userClient.getUsersByIds(reporterIds);
        userMap = users.stream().collect(Collectors.toMap(UserExternalDto::getId, u -> u));
      } catch (Exception e) {
        log.warn("Unable to get the information: {}", e.getMessage());
      }
    }

    Map<Long, UserExternalDto> finalUserMap = userMap;
    List<ReportResponse> content = page.getContent().stream().map(report -> {
      ReportResponse response = reportMapper.toResponse(report);

      if (report.getReporterId() != null && finalUserMap.containsKey(report.getReporterId())) {
        UserExternalDto user = finalUserMap.get(report.getReporterId());
        response.setReporterName(user.getDisplayName());
        response.setReporterAvatar(user.getAvatarUrl());
      }

      if (response.getStatus() == null) response.setStatus(ReportStatus.PENDING);
      if (response.getIsBannedBySystem() == null) response.setIsBannedBySystem(false);

      return response;
    }).toList();

    return PageVO.<ReportResponse>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(content.size())
        .content(content)
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ComplaintResponse> getComplaintsByContent(
      String targetId, TargetType targetType, Pageable pageable) {

    Page<Complaint> page = complaintRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable);
    if (page.isEmpty()) return buildEmptyPageVO(page);

    List<Long> userIds = page.getContent().stream()
        .map(Complaint::getUserId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();

    Map<Long, UserExternalDto> userMap = new HashMap<>();
    if (!userIds.isEmpty()) {
      try {
        List<UserExternalDto> users = userClient.getUsersByIds(userIds);
        userMap = users.stream().collect(Collectors.toMap(UserExternalDto::getId, u -> u));
      } catch (Exception e) {
        log.warn("Unable to get the information {}", e.getMessage());
      }
    }

    Map<Long, UserExternalDto> finalUserMap = userMap;
    List<ComplaintResponse> content = page.getContent().stream().map(complaint -> {
      ComplaintResponse response = reportMapper.toResponse(complaint);

      if (complaint.getUserId() != null && finalUserMap.containsKey(complaint.getUserId())) {
        UserExternalDto user = finalUserMap.get(complaint.getUserId());
        response.setUserDisplayName(user.getDisplayName());
      }

      if (response.getStatus() == null) response.setStatus(ComplaintStatus.PENDING);

      return response;
    }).toList();

    return PageVO.<ComplaintResponse>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(content.size())
        .content(content)
        .build();
  }

  @Override
  @Transactional
  public ComplaintResponse createComplaint(CreateComplaintRequest request) {
    String userIdStr = SecurityContextHolder.getContext().getAuthentication().getName();
    Long currentUserId = Long.parseLong(userIdStr);

    if (complaintRepository.existsByTargetTypeAndTargetId(
        request.getTargetType(), request.getTargetId())) {
      throw new BadRequestException(
          "Nội dung này đang có khiếu nại chờ xử lý hoặc đã được giải quyết.");
    }

    // Verify ownership qua HTTP Client
    verifyContentOwnership(currentUserId, request.getTargetType(), request.getTargetId());

    Complaint complaint =
        Complaint.builder()
            .userId(currentUserId)
            .targetType(request.getTargetType())
            .targetId(request.getTargetId())
            .content(request.getReason())
            .status(ComplaintStatus.PENDING)
            .build();

    return reportMapper.toResponse(complaintRepository.save(complaint));
  }

  @Override
  @Transactional
  public ComplaintResponse updateComplaint(Long id, ComplaintStatus status) {
    Complaint complaint =
        complaintRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));

    complaint.setStatus(status);
    complaintRepository.save(complaint);

    return reportMapper.toResponse(complaint);
  }

  private void verifyContentOwnership(Long userId, TargetType type, String targetId) {
    try {
      Long ownerId =
          switch (type) {
            case POST -> mediaClient.getPostOwnerId(targetId);
            case COMMENT -> mediaClient.getCommentOwnerId(targetId);
            case USER -> Long.valueOf(targetId);
            case MESSAGE -> chatClient.getMessageOwnerId(targetId);
            default -> throw new BadRequestException("Loại nội dung không hỗ trợ khiếu nại.");
          };

      if (!userId.equals(ownerId)) {
        throw new BadRequestException(
            "Bạn chỉ có thể khiếu nại cho nội dung do chính mình tạo ra.");
      }
    } catch (BadRequestException e) {
      throw e; // Rethrow để trả về lỗi rõ ràng cho client
    } catch (Exception e) {
      log.warn("Không thể gọi service để verify ownership lúc này: {}", e.getMessage());
    }
  }

  private <T> PageVO<T> buildEmptyPageVO(Page<?> page) {
    return PageVO.<T>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(0)
        .content(Collections.emptyList())
        .build();
  }
}
