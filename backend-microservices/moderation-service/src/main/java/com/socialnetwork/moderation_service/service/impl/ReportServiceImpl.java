package com.socialnetwork.moderation_service.service.impl;

import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ConflictException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.security.SecurityUtils;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.client.ChatClient;
import com.socialnetwork.moderation_service.client.MediaClient;
import com.socialnetwork.moderation_service.client.UserClient;
import com.socialnetwork.moderation_service.dto.ComplaintResponse;
import com.socialnetwork.moderation_service.dto.CreateComplaintRequest;
import com.socialnetwork.moderation_service.dto.CreateReportRequest;
import com.socialnetwork.moderation_service.dto.ReportResponse;
import com.socialnetwork.moderation_service.dto.UpdateReportRequest;
import com.socialnetwork.moderation_service.dto.external.UserExternalDto;
import com.socialnetwork.moderation_service.enums.ComplaintStatus;
import com.socialnetwork.moderation_service.enums.ReportReason;
import com.socialnetwork.moderation_service.enums.ReportSource;
import com.socialnetwork.moderation_service.enums.ReportStatus;
import com.socialnetwork.moderation_service.mapper.ReportMapper;
import com.socialnetwork.moderation_service.model.Complaint;
import com.socialnetwork.moderation_service.model.Report;
import com.socialnetwork.moderation_service.repository.ComplaintRepository;
import com.socialnetwork.moderation_service.repository.ReportRepository;
import com.socialnetwork.moderation_service.service.ModerationService;
import com.socialnetwork.moderation_service.service.ReportService;
import io.github.perplexhub.rsql.RSQLJPASupport;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  private final ModerationService moderationService;

  // ── Reports ──────────────────────────────────────────────────────────────

  @Override
  @Transactional
  public ReportResponse createReport(Long reporterId, CreateReportRequest request) {
    if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
        reporterId, request.getTargetType(), request.getTargetId())) {
      throw new ConflictException("You have already reported this content.");
    }

    Long targetOwnerId = resolveOwnerId(request.getTargetType(), request.getTargetId());
    if (targetOwnerId == null) {
      throw new BadRequestException("The reported content no longer has an owner.");
    }
    if (reporterId.equals(targetOwnerId)) {
      throw new BadRequestException("You cannot report your own content.");
    }

    Report report =
        Report.builder()
            .source(ReportSource.USER)
            .status(ReportStatus.PENDING)
            .reporterId(reporterId)
            .targetType(request.getTargetType())
            .targetId(request.getTargetId())
            .targetUserId(targetOwnerId)
            .reason(request.getReason())
            .customReason(
                request.getReason() == ReportReason.OTHER ? request.getCustomReason() : null)
            .bannedBySystem(false)
            .build();

    log.info(
        "User {} reported {} {}", reporterId, request.getTargetType(), request.getTargetId());
    return reportMapper.toResponse(reportRepository.save(report));
  }

  @Override
  @Transactional
  public List<ReportResponse> updateReport(UpdateReportRequest request) {
    List<Report> reports = reportRepository.findAllById(request.getReportIds());
    if (reports.isEmpty()) {
      throw new ResourceNotFoundException("No reports found for the provided IDs");
    }

    ReportStatus newStatus = request.getReportStatus();
    for (Report report : reports) {
      boolean newlyApproved =
          newStatus == ReportStatus.APPROVED && report.getStatus() != ReportStatus.APPROVED;
      report.setStatus(newStatus);
      if (newlyApproved) {
        // Approving a report means the content really is in breach: hide it.
        moderationService.blockContent(
            report.getTargetId(), report.getTargetType(), report.getId());
      }
    }

    return reportRepository.saveAll(reports).stream().map(reportMapper::toResponse).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ReportResponse getReportById(Long reportId) {
    return reportMapper.toResponse(
        reportRepository
            .findById(reportId)
            .orElseThrow(() -> new ResourceNotFoundException("Report not found")));
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ReportResponse> getReports(String filter, Pageable pageable) {
    Specification<Report> spec =
        (filter == null || filter.isBlank())
            ? (root, query, cb) -> cb.conjunction()
            : RSQLJPASupport.toSpecification(filter);

    return PageVO.from(reportRepository.findAll(spec, pageable), reportMapper::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ReportResponse> getReportsByContent(
      String targetId, TargetType targetType, Pageable pageable) {
    Page<Report> page = reportRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable);
    if (page.isEmpty()) {
      return PageVO.emptyPage(page);
    }

    Map<Long, UserExternalDto> reporters =
        loadUsers(page.getContent().stream().map(Report::getReporterId).toList());

    return PageVO.from(
        page,
        report -> {
          ReportResponse response = reportMapper.toResponse(report);
          UserExternalDto reporter = reporters.get(report.getReporterId());
          if (reporter != null) {
            response.setReporterName(reporter.getDisplayName());
            response.setReporterAvatar(reporter.getAvatarUrl());
          }
          return response;
        });
  }

  // ── Complaints ───────────────────────────────────────────────────────────

  @Override
  @Transactional
  public ComplaintResponse createComplaint(CreateComplaintRequest request) {
    Long currentUserId = SecurityUtils.getCurrentUserId();

    if (complaintRepository.existsByUserIdAndTargetTypeAndTargetIdAndStatus(
        currentUserId, request.getTargetType(), request.getTargetId(), ComplaintStatus.PENDING)) {
      throw new ConflictException("You already have a pending complaint about this content.");
    }

    verifyOwnership(currentUserId, request.getTargetType(), request.getTargetId());

    Complaint complaint =
        Complaint.builder()
            .userId(currentUserId)
            .targetType(request.getTargetType())
            .targetId(request.getTargetId())
            .content(request.getReason())
            .status(ComplaintStatus.PENDING)
            .build();

    log.info(
        "User {} complained about {} {}",
        currentUserId,
        request.getTargetType(),
        request.getTargetId());
    return reportMapper.toResponse(complaintRepository.save(complaint));
  }

  @Override
  @Transactional
  public ComplaintResponse updateComplaint(Long id, ComplaintStatus status) {
    Complaint complaint =
        complaintRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));

    boolean newlyApproved =
        status == ComplaintStatus.APPROVED && complaint.getStatus() != ComplaintStatus.APPROVED;
    complaint.setStatus(status);
    complaintRepository.save(complaint);

    if (newlyApproved) {
      // Accepting a complaint means the content was hidden by mistake: restore it.
      moderationService.unblockContent(
          complaint.getTargetId(), complaint.getTargetType(), complaint.getId());
    }

    return reportMapper.toResponse(complaint);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ComplaintResponse> getComplaints(String filter, Pageable pageable) {
    Specification<Complaint> spec =
        (filter == null || filter.isBlank())
            ? (root, query, cb) -> cb.conjunction()
            : RSQLJPASupport.toSpecification(filter);

    return PageVO.from(complaintRepository.findAll(spec, pageable), reportMapper::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public ComplaintResponse getComplaintById(Long id) {
    return reportMapper.toResponse(
        complaintRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Complaint not found")));
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ComplaintResponse> getComplaintsByContent(
      String targetId, TargetType targetType, Pageable pageable) {
    Page<Complaint> page =
        complaintRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable);
    if (page.isEmpty()) {
      return PageVO.emptyPage(page);
    }

    Map<Long, UserExternalDto> authors =
        loadUsers(page.getContent().stream().map(Complaint::getUserId).toList());

    return PageVO.from(
        page,
        complaint -> {
          ComplaintResponse response = reportMapper.toResponse(complaint);
          UserExternalDto author = authors.get(complaint.getUserId());
          if (author != null) {
            response.setUserDisplayName(author.getDisplayName());
          }
          return response;
        });
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  /**
   * Owner of the target, asked to the service that owns it. Failures are never swallowed: the
   * shared handler turns them into 404 / 502 / 503 so a report or complaint is refused rather than
   * created against an unverified owner.
   */
  private Long resolveOwnerId(TargetType targetType, String targetId) {
    return switch (targetType) {
      case POST -> mediaClient.getPostOwnerId(targetId);
      case COMMENT -> mediaClient.getCommentOwnerId(targetId);
      case MESSAGE -> chatClient.getMessageOwnerId(targetId);
      case USER -> parseUserId(targetId);
      case MEDIA, REPORT -> throw new BadRequestException(
          "This kind of content cannot be reported.");
    };
  }

  /** Fails closed: a complaint is only accepted when the caller is provably the content owner. */
  private void verifyOwnership(Long userId, TargetType targetType, String targetId) {
    Long ownerId =
        switch (targetType) {
          case POST -> mediaClient.getPostOwnerId(targetId);
          case COMMENT -> mediaClient.getCommentOwnerId(targetId);
          case MESSAGE -> chatClient.getMessageOwnerId(targetId);
          case USER -> parseUserId(targetId);
          case MEDIA, REPORT -> throw new BadRequestException(
              "This kind of content cannot be appealed.");
        };

    if (ownerId == null) {
      throw new BadRequestException("The owner of this content could not be determined.");
    }
    if (!userId.equals(ownerId)) {
      throw new BadRequestException("You can only appeal content you created yourself.");
    }
  }

  private static Long parseUserId(String targetId) {
    try {
      return Long.valueOf(targetId);
    } catch (NumberFormatException e) {
      throw new BadRequestException("Invalid user id: " + targetId);
    }
  }

  private Map<Long, UserExternalDto> loadUsers(List<Long> ids) {
    List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
    if (distinct.isEmpty()) {
      return new HashMap<>();
    }
    return userClient.getUsersByIds(distinct).stream()
        .collect(Collectors.toMap(UserExternalDto::getId, user -> user));
  }
}
