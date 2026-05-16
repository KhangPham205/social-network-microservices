package com.socialnetwork.moderation_service.service;

import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.moderation_service.dto.*;
import com.socialnetwork.moderation_service.enums.ComplaintStatus;
import com.socialnetwork.moderation_service.mapper.ReportMapper;
import com.socialnetwork.moderation_service.model.Complaint;
import com.socialnetwork.moderation_service.model.Report;
import com.socialnetwork.moderation_service.repository.ComplaintRepository;
import com.socialnetwork.moderation_service.repository.ReportRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import vo.PageVO;
import vo.TargetType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final ComplaintRepository complaintRepository;
    private final ReportMapper reportMapper;
    private final RestTemplate restTemplate; // For calling other services

    @Override
    @Transactional
    public ReportResponse createReport(Long reporterId, CreateReportRequest request) {
        // Check if reporter has already reported this content
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, request.getTargetType(), request.getTargetId())) {
            throw new BadRequestException("Bạn đã báo cáo nội dung này rồi.");
        }

        // Determine target owner ID
        Long targetOwnerId = determineTargetOwnerId(request.getTargetType(), request.getTargetId(), reporterId);

        if (targetOwnerId == null) {
            throw new BadRequestException("Không xác định được chủ sở hữu nội dung");
        }

        // Create Report without User entity dependency
        Report report = Report.builder()
                .reporterId(reporterId) // Store ID instead of entity
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .targetUserId(targetOwnerId)
                .reason(request.getReason())
                .customReason(request.getReason().name().equals("OTHER") ? request.getCustomReason() : null)
                .build();

        return reportMapper.toResponse(reportRepository.save(report));
    }

    /**
     * Determine target owner ID by calling other microservices
     */
    private Long determineTargetOwnerId(TargetType targetType, String targetId, Long reporterId) {
        try {
            switch (targetType) {
                case POST:
                    // Call post-service to get post owner
                    // return restTemplate.getForObject("http://post-service/api/v1/posts/" + targetId + "/author", Long.class);
                    return tryGetFromService("http://post-service/api/v1/posts/" + targetId + "/owner-id");

                case COMMENT:
                    // Call comment-service or post-service for comment
                    return tryGetFromService("http://post-service/api/v1/comments/" + targetId + "/owner-id");

                case USER:
                    return Long.valueOf(targetId);

                case MESSAGE:
                    return tryGetFromService("http://chat-service/api/v1/messages/" + targetId + "/from-user");

                default:
                    return null;
            }
        } catch (Exception e) {
            // If service is not available, return null or handle gracefully
            return null;
        }
    }

    private Long tryGetFromService(String url) {
        try {
            return restTemplate.getForObject(url, Long.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
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

        return updatedReports.stream()
                .map(reportMapper::toResponse)
                .toList();
    }

    @Override
    public ReportResponse getReportById(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        return reportMapper.toResponse(report);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<ReportResponse> getReports(String filter, Pageable pageable) {
        Specification<Report> spec = Specification.where(null);

        if (filter != null && !filter.isBlank()) {
            Map<String, String> propertyPathMapper = new HashMap<>();
            propertyPathMapper.put("id", "id");
            propertyPathMapper.put("targetType", "targetType"); // filter=targetType=='POST'
            propertyPathMapper.put("reason", "reason");
            propertyPathMapper.put("reporter", "reporter.username"); // filter=reporter=='nguyenvana'
            propertyPathMapper.put("targetUserId", "targetUserId");  // filter=targetUserId==10 (Xem ai bị report nhiều)

            spec = RSQLJPASupport.toSpecification(filter, propertyPathMapper);
        }

        Page<Report> page = reportRepository.findAll(spec, pageable);

        List<ReportResponse> content = page.getContent().stream()
                .map(reportMapper::toResponse)
                .toList();

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
        Specification<Complaint> spec = Specification.where(null);

        if (filter != null && !filter.isBlank()) {
            Map<String, String> propertyPathMapper = new HashMap<>();
            propertyPathMapper.put("reportId", "report.id");
            propertyPathMapper.put("userId", "user.id");
            propertyPathMapper.put("username", "user.username");
            propertyPathMapper.put("email", "user.email");
            propertyPathMapper.put("createdAt", "createdAt");

            spec = RSQLJPASupport.toSpecification(filter, propertyPathMapper);
        }

        Page<Complaint> page = complaintRepository.findAll(spec, pageable);

        List<ComplaintResponse> content = page.getContent().stream()
                .map(reportMapper::toResponse)
                .toList();

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
    public ComplaintResponse getComplaintById(Long id) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));
        return reportMapper.toResponse(complaint);
    }

    @Override
    @Transactional(readOnly = true)
    public PageVO<ReportResponse> getReportsByContent(String targetId, TargetType targetType, Pageable pageable) {
        Page<Report> page = reportRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable);

        List<ReportResponse> content = page.getContent().stream()
                .map(reportMapper::toResponse)
                .toList();

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
    public PageVO<ComplaintResponse> getComplaintsByContent(String targetId, TargetType targetType, Pageable pageable) {
        Page<Complaint> page = complaintRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable);

        List<ComplaintResponse> content = page.getContent().stream()
                .map(reportMapper::toResponse)
                .toList();

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
        // Get current user ID from Security Context
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = Long.parseLong(auth.getName());

        // 1. Check for duplicate complaint
        if (complaintRepository.existsByTargetTypeAndTargetId(request.getTargetType(), request.getTargetId())) {
            throw new BadRequestException("Nội dung này đang có khiếu nại chờ xử lý hoặc đã được giải quyết.");
        }

        // 2. Verify ownership through REST call or skip if service unavailable
        verifyContentOwnership(currentUserId, request.getTargetType(), request.getTargetId());

        // 3. Create Complaint without User entity dependency
        Complaint complaint = Complaint.builder()
                .userId(currentUserId) // Store ID instead of entity
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .content(request.getReason())
                .status(ComplaintStatus.PENDING)
                .build();

        return reportMapper.toResponse(complaintRepository.save(complaint));
    }

    private void verifyContentOwnership(Long userId, TargetType type, String targetId) {
        try {
            switch (type) {
                case POST:
                    Long postOwnerId = restTemplate.getForObject(
                        "http://post-service/api/v1/posts/" + targetId + "/owner-id", Long.class);
                    if (!postOwnerId.equals(userId)) {
                        throw new BadRequestException("Bạn chỉ có thể khiếu nại cho bài viết của chính mình.");
                    }
                    break;

                case COMMENT:
                    Long commentOwnerId = restTemplate.getForObject(
                        "http://post-service/api/v1/comments/" + targetId + "/owner-id", Long.class);
                    if (!commentOwnerId.equals(userId)) {
                        throw new BadRequestException("Bạn chỉ có thể khiếu nại cho bình luận của chính mình.");
                    }
                    break;

                case USER:
                    if (!Long.valueOf(targetId).equals(userId)) {
                        throw new BadRequestException("Bạn chỉ có thể khiếu nại cho chính mình.");
                    }
                    break;

                default:
                    throw new BadRequestException("Loại nội dung không hỗ trợ khiếu nại.");
            }
        } catch (Exception e) {
            // If service unavailable, allow complaint creation but log the error
            // In production, implement retry logic or fallback
        }
    }
}