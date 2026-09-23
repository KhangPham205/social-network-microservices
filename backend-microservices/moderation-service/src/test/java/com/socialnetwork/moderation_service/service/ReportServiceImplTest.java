package com.socialnetwork.moderation_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ConflictException;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.client.ChatClient;
import com.socialnetwork.moderation_service.client.MediaClient;
import com.socialnetwork.moderation_service.client.UserClient;
import com.socialnetwork.moderation_service.dto.ComplaintResponse;
import com.socialnetwork.moderation_service.dto.CreateComplaintRequest;
import com.socialnetwork.moderation_service.dto.CreateReportRequest;
import com.socialnetwork.moderation_service.dto.ReportResponse;
import com.socialnetwork.moderation_service.enums.ComplaintStatus;
import com.socialnetwork.moderation_service.enums.ReportReason;
import com.socialnetwork.moderation_service.enums.ReportSource;
import com.socialnetwork.moderation_service.enums.ReportStatus;
import com.socialnetwork.moderation_service.mapper.ReportMapper;
import com.socialnetwork.moderation_service.model.Complaint;
import com.socialnetwork.moderation_service.model.Report;
import com.socialnetwork.moderation_service.repository.ComplaintRepository;
import com.socialnetwork.moderation_service.repository.ReportRepository;
import com.socialnetwork.moderation_service.service.impl.ReportServiceImpl;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.ResourceAccessException;

/** Business rules of {@link ReportServiceImpl}: deduplication, self-reports and fail-closed checks. */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

  private static final Long CALLER_ID = 7L;
  private static final String POST_ID = "100";

  @Mock private ReportRepository reportRepository;
  @Mock private ComplaintRepository complaintRepository;
  @Mock private ReportMapper reportMapper;
  @Mock private UserClient userClient;
  @Mock private MediaClient mediaClient;
  @Mock private ChatClient chatClient;
  @Mock private ModerationService moderationService;

  @InjectMocks private ReportServiceImpl reportService;

  @BeforeEach
  void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(CALLER_ID.toString(), null, List.of()));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private CreateReportRequest reportRequest() {
    CreateReportRequest request = new CreateReportRequest();
    request.setTargetType(TargetType.POST);
    request.setTargetId(POST_ID);
    request.setReason(ReportReason.SPAM);
    return request;
  }

  // ── createReport ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("A user may report the same target only once")
  void duplicateReportIsRejected() {
    when(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
            CALLER_ID, TargetType.POST, POST_ID))
        .thenReturn(true);

    assertThatThrownBy(() -> reportService.createReport(CALLER_ID, reportRequest()))
        .isInstanceOf(ConflictException.class);

    verify(reportRepository, never()).save(any());
  }

  @Test
  @DisplayName("Reporting your own content is rejected")
  void selfReportIsRejected() {
    when(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
            CALLER_ID, TargetType.POST, POST_ID))
        .thenReturn(false);
    when(mediaClient.getPostOwnerId(POST_ID)).thenReturn(CALLER_ID);

    assertThatThrownBy(() -> reportService.createReport(CALLER_ID, reportRequest()))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("your own content");

    verify(reportRepository, never()).save(any());
  }

  @Test
  @DisplayName("An unreachable owner service fails the report instead of guessing an owner")
  void reportFailsClosedWhenOwnerCannotBeResolved() {
    when(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
            CALLER_ID, TargetType.POST, POST_ID))
        .thenReturn(false);
    when(mediaClient.getPostOwnerId(POST_ID))
        .thenThrow(new ResourceAccessException("media-service is down"));

    assertThatThrownBy(() -> reportService.createReport(CALLER_ID, reportRequest()))
        .isInstanceOf(ResourceAccessException.class);

    verify(reportRepository, never()).save(any());
  }

  @Test
  @DisplayName("A valid report is stored as a USER report in PENDING state")
  void validReportIsStored() {
    when(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
            CALLER_ID, TargetType.POST, POST_ID))
        .thenReturn(false);
    when(mediaClient.getPostOwnerId(POST_ID)).thenReturn(99L);
    when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));
    when(reportMapper.toResponse(any(Report.class))).thenReturn(new ReportResponse());

    reportService.createReport(CALLER_ID, reportRequest());

    ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
    verify(reportRepository).save(saved.capture());
    assertThat(saved.getValue().getSource()).isEqualTo(ReportSource.USER);
    assertThat(saved.getValue().getStatus()).isEqualTo(ReportStatus.PENDING);
    assertThat(saved.getValue().getReporterId()).isEqualTo(CALLER_ID);
    assertThat(saved.getValue().getTargetUserId()).isEqualTo(99L);
    assertThat(saved.getValue().isBannedBySystem()).isFalse();
  }

  // ── createComplaint ──────────────────────────────────────────────────────

  private CreateComplaintRequest complaintRequest() {
    return new CreateComplaintRequest(POST_ID, TargetType.POST, "this was a mistake");
  }

  @Test
  @DisplayName("Only one PENDING complaint per user and target is allowed")
  void duplicatePendingComplaintIsRejected() {
    when(complaintRepository.existsByUserIdAndTargetTypeAndTargetIdAndStatus(
            CALLER_ID, TargetType.POST, POST_ID, ComplaintStatus.PENDING))
        .thenReturn(true);

    assertThatThrownBy(() -> reportService.createComplaint(complaintRequest()))
        .isInstanceOf(ConflictException.class);

    verify(complaintRepository, never()).save(any());
  }

  @Test
  @DisplayName("A decided complaint does not block a new one: deduplication is per PENDING only")
  void aDecidedComplaintDoesNotBlockANewOne() {
    when(complaintRepository.existsByUserIdAndTargetTypeAndTargetIdAndStatus(
            CALLER_ID, TargetType.POST, POST_ID, ComplaintStatus.PENDING))
        .thenReturn(false);
    when(mediaClient.getPostOwnerId(POST_ID)).thenReturn(CALLER_ID);
    when(complaintRepository.save(any(Complaint.class))).thenAnswer(i -> i.getArgument(0));
    when(reportMapper.toResponse(any(Complaint.class))).thenReturn(new ComplaintResponse());

    reportService.createComplaint(complaintRequest());

    ArgumentCaptor<Complaint> saved = ArgumentCaptor.forClass(Complaint.class);
    verify(complaintRepository).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(CALLER_ID);
    assertThat(saved.getValue().getStatus()).isEqualTo(ComplaintStatus.PENDING);
  }

  @Test
  @DisplayName("Complaining about somebody else's content is rejected")
  void complaintAboutForeignContentIsRejected() {
    when(complaintRepository.existsByUserIdAndTargetTypeAndTargetIdAndStatus(
            CALLER_ID, TargetType.POST, POST_ID, ComplaintStatus.PENDING))
        .thenReturn(false);
    when(mediaClient.getPostOwnerId(POST_ID)).thenReturn(4242L);

    assertThatThrownBy(() -> reportService.createComplaint(complaintRequest()))
        .isInstanceOf(BadRequestException.class);

    verify(complaintRepository, never()).save(any());
  }

  @Test
  @DisplayName("Ownership that cannot be verified fails closed: the complaint is not created")
  void complaintFailsClosedWhenOwnershipCannotBeVerified() {
    when(complaintRepository.existsByUserIdAndTargetTypeAndTargetIdAndStatus(
            CALLER_ID, TargetType.POST, POST_ID, ComplaintStatus.PENDING))
        .thenReturn(false);
    when(mediaClient.getPostOwnerId(POST_ID))
        .thenThrow(new ResourceAccessException("media-service is down"));

    assertThatThrownBy(() -> reportService.createComplaint(complaintRequest()))
        .isInstanceOf(ResourceAccessException.class);

    verify(complaintRepository, never()).save(any());
  }

  @Test
  @DisplayName("An unknown owner fails closed with 400 rather than creating the complaint")
  void complaintFailsClosedWhenOwnerIsUnknown() {
    when(complaintRepository.existsByUserIdAndTargetTypeAndTargetIdAndStatus(
            CALLER_ID, TargetType.MESSAGE, "abc", ComplaintStatus.PENDING))
        .thenReturn(false);
    when(chatClient.getMessageOwnerId("abc")).thenReturn(null);

    CreateComplaintRequest request =
        new CreateComplaintRequest("abc", TargetType.MESSAGE, "restore my message");

    assertThatThrownBy(() -> reportService.createComplaint(request))
        .isInstanceOf(BadRequestException.class);

    verify(complaintRepository, never()).save(any());
  }

  // ── Decisions trigger the matching moderation action ─────────────────────

  @Test
  @DisplayName("Approving a complaint restores the content it is about")
  void approvingAComplaintUnblocksTheContent() {
    Complaint complaint =
        Complaint.builder()
            .userId(CALLER_ID)
            .targetType(TargetType.MESSAGE)
            .targetId("65f1c0ffee0000000000dead")
            .status(ComplaintStatus.PENDING)
            .build();
    complaint.setId(9L);

    when(complaintRepository.findById(9L)).thenReturn(java.util.Optional.of(complaint));
    when(complaintRepository.save(any(Complaint.class))).thenAnswer(i -> i.getArgument(0));
    when(reportMapper.toResponse(any(Complaint.class))).thenReturn(new ComplaintResponse());

    reportService.updateComplaint(9L, ComplaintStatus.APPROVED);

    verify(moderationService).unblockContent("65f1c0ffee0000000000dead", TargetType.MESSAGE, 9L);
  }

  @Test
  @DisplayName("Approving a report hides the reported content")
  void approvingAReportBlocksTheContent() {
    Report report =
        Report.builder()
            .source(ReportSource.USER)
            .status(ReportStatus.PENDING)
            .reporterId(CALLER_ID)
            .targetType(TargetType.POST)
            .targetId(POST_ID)
            .targetUserId(99L)
            .reason(ReportReason.SPAM)
            .build();
    report.setId(3L);

    when(reportRepository.findAllById(List.of(3L))).thenReturn(List.of(report));
    when(reportRepository.saveAll(List.of(report))).thenReturn(List.of(report));
    when(reportMapper.toResponse(any(Report.class))).thenReturn(new ReportResponse());

    reportService.updateReport(
        com.socialnetwork.moderation_service.dto.UpdateReportRequest.builder()
            .reportIds(List.of(3L))
            .reportStatus(ReportStatus.APPROVED)
            .build());

    verify(moderationService).blockContent(POST_ID, TargetType.POST, 3L);
  }
}
