package com.socialnetwork.moderation_service.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.ContentCreatedEvent;
import com.socialnetwork.common.events.MessageCreatedEvent;
import com.socialnetwork.common.events.ModerationActionEvent;
import com.socialnetwork.common.vo.ModerationAction;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.client.AiServiceClient;
import com.socialnetwork.moderation_service.client.AuthClient;
import com.socialnetwork.moderation_service.client.ChatClient;
import com.socialnetwork.moderation_service.client.MediaClient;
import com.socialnetwork.moderation_service.client.UserClient;
import com.socialnetwork.moderation_service.dto.AiModerationRequest;
import com.socialnetwork.moderation_service.dto.AiModerationResponse;
import com.socialnetwork.moderation_service.enums.ModerationLogAction;
import com.socialnetwork.moderation_service.enums.ReportReason;
import com.socialnetwork.moderation_service.enums.ReportSource;
import com.socialnetwork.moderation_service.mapper.ReportMapper;
import com.socialnetwork.moderation_service.model.ModerationLog;
import com.socialnetwork.moderation_service.model.Report;
import com.socialnetwork.moderation_service.repository.ComplaintRepository;
import com.socialnetwork.moderation_service.repository.ModerationLogRepository;
import com.socialnetwork.moderation_service.repository.ReportRepository;
import com.socialnetwork.moderation_service.service.KafkaEventPublisher;
import com.socialnetwork.moderation_service.service.impl.ModerationServiceImpl;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

/**
 * Covers the AI moderation pipeline end to end at unit level: the Kafka listener hands the common
 * event records to the real {@link ModerationServiceImpl}, everything it talks to is mocked.
 */
@ExtendWith(MockitoExtension.class)
class ContentModerationListenerTest {

  private static final long SAVED_REPORT_ID = 500L;

  @Mock private UserClient userClient;
  @Mock private MediaClient mediaClient;
  @Mock private ChatClient chatClient;
  @Mock private AuthClient authClient;
  @Mock private AiServiceClient aiServiceClient;
  @Mock private ReportRepository reportRepository;
  @Mock private ComplaintRepository complaintRepository;
  @Mock private ModerationLogRepository moderationLogRepository;
  @Mock private ReportMapper reportMapper;
  @Mock private KafkaEventPublisher eventPublisher;

  private ContentModerationListener listener;

  @BeforeEach
  void setUp() {
    ModerationServiceImpl moderationService =
        new ModerationServiceImpl(
            userClient,
            mediaClient,
            chatClient,
            authClient,
            aiServiceClient,
            reportRepository,
            complaintRepository,
            moderationLogRepository,
            reportMapper,
            eventPublisher);
    listener = new ContentModerationListener(moderationService);
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private void givenVerdict(boolean toxic, String reason) {
    when(aiServiceClient.checkToxicity(any(AiModerationRequest.class)))
        .thenReturn(new AiModerationResponse(toxic, toxic ? 0.97 : 0.02, reason));
  }

  private void givenNotYetModerated() {
    when(reportRepository.existsByTargetTypeAndTargetIdAndSource(
            any(TargetType.class), anyString(), eq(ReportSource.SYSTEM)))
        .thenReturn(false);
    when(reportRepository.save(any(Report.class)))
        .thenAnswer(
            invocation -> {
              Report report = invocation.getArgument(0);
              report.setId(SAVED_REPORT_ID);
              return report;
            });
  }

  private ModerationActionEvent capturePublishedAction() {
    ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher, times(1))
        .publishAfterCommit(topic.capture(), key.capture(), event.capture());

    assertThat(topic.getValue()).isEqualTo(KafkaTopics.MODERATION_ACTIONS);
    assertThat(event.getValue()).isInstanceOf(ModerationActionEvent.class);
    ModerationActionEvent action = (ModerationActionEvent) event.getValue();
    assertThat(key.getValue()).isEqualTo(action.targetId());
    return action;
  }

  // ── Content events ───────────────────────────────────────────────────────

  @Test
  @DisplayName("Toxic post: exactly one system report, one AUTO_BAN log and one BLOCK event")
  void toxicPostIsAutoBannedOnce() {
    givenVerdict(true, "hate_speech");
    givenNotYetModerated();

    listener.onContentCreated(
        new ContentCreatedEvent(42L, TargetType.POST, "hateful text", 7L, null));

    ArgumentCaptor<AiModerationRequest> aiRequest =
        ArgumentCaptor.forClass(AiModerationRequest.class);
    verify(aiServiceClient).checkToxicity(aiRequest.capture());
    assertThat(aiRequest.getValue().getText()).isEqualTo("hateful text");

    ArgumentCaptor<Report> report = ArgumentCaptor.forClass(Report.class);
    verify(reportRepository, times(1)).save(report.capture());
    assertThat(report.getValue().getSource()).isEqualTo(ReportSource.SYSTEM);
    assertThat(report.getValue().getReporterId()).isNull();
    assertThat(report.getValue().getReason()).isEqualTo(ReportReason.AI_DETECTED);
    assertThat(report.getValue().getTargetType()).isEqualTo(TargetType.POST);
    assertThat(report.getValue().getTargetId()).isEqualTo("42");
    assertThat(report.getValue().getTargetUserId()).isEqualTo(7L);
    assertThat(report.getValue().isBannedBySystem()).isTrue();

    ArgumentCaptor<ModerationLog> logEntry = ArgumentCaptor.forClass(ModerationLog.class);
    verify(moderationLogRepository, times(1)).save(logEntry.capture());
    assertThat(logEntry.getValue().getAction()).isEqualTo(ModerationLogAction.AUTO_BAN);
    assertThat(logEntry.getValue().getActorId()).isNull();
    assertThat(logEntry.getValue().getReportId()).isEqualTo(SAVED_REPORT_ID);
    assertThat(logEntry.getValue().getReason()).contains("hate_speech");

    ModerationActionEvent action = capturePublishedAction();
    assertThat(action.action()).isEqualTo(ModerationAction.BLOCK);
    assertThat(action.targetType()).isEqualTo(TargetType.POST);
    assertThat(action.targetId()).isEqualTo("42");
  }

  @Test
  @DisplayName("Toxic comment: the COMMENT target type survives the whole pipeline")
  void toxicCommentKeepsItsTargetType() {
    givenVerdict(true, "abusive_language");
    givenNotYetModerated();

    listener.onContentCreated(
        new ContentCreatedEvent(7L, TargetType.COMMENT, "abuse", 5L, null));

    ArgumentCaptor<Report> report = ArgumentCaptor.forClass(Report.class);
    verify(reportRepository).save(report.capture());
    assertThat(report.getValue().getTargetType()).isEqualTo(TargetType.COMMENT);
    assertThat(capturePublishedAction().targetType()).isEqualTo(TargetType.COMMENT);
  }

  @Test
  @DisplayName("Media attached to the event is forwarded to the model")
  void mediaIsForwardedToTheModel() {
    givenVerdict(true, "nsfw_image");
    givenNotYetModerated();

    listener.onContentCreated(
        new ContentCreatedEvent(
            12L,
            TargetType.POST,
            "post with image",
            2L,
            List.of(Map.of("url", "http://img.test/1.jpg", "type", "IMAGE"))));

    ArgumentCaptor<AiModerationRequest> aiRequest =
        ArgumentCaptor.forClass(AiModerationRequest.class);
    verify(aiServiceClient).checkToxicity(aiRequest.capture());
    assertThat(aiRequest.getValue().getMedia())
        .isNotNull()
        .first()
        .satisfies(entry -> assertThat(entry).containsEntry("url", "http://img.test/1.jpg"));
  }

  @Test
  @DisplayName("Clean content: nothing is written and nothing is published")
  void cleanContentIsLeftAlone() {
    givenVerdict(false, null);

    listener.onContentCreated(
        new ContentCreatedEvent(99L, TargetType.POST, "a wholesome family post", 3L, null));

    verify(reportRepository, never()).save(any());
    verify(moderationLogRepository, never()).save(any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("Already auto-moderated target: the redelivered event writes nothing twice")
  void redeliveredEventIsIdempotent() {
    givenVerdict(true, "spam");
    when(reportRepository.existsByTargetTypeAndTargetIdAndSource(
            TargetType.POST, "55", ReportSource.SYSTEM))
        .thenReturn(true);

    listener.onContentCreated(
        new ContentCreatedEvent(55L, TargetType.POST, "already banned", 9L, null));

    verify(reportRepository, never()).save(any());
    verify(moderationLogRepository, never()).save(any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("A failing AI scan propagates so Kafka retries and finally dead-letters the record")
  void aiFailurePropagates() {
    when(aiServiceClient.checkToxicity(any(AiModerationRequest.class)))
        .thenThrow(new ResourceAccessException("ai-service timed out"));

    ContentCreatedEvent event =
        new ContentCreatedEvent(1L, TargetType.POST, "unscanned text", 4L, null);

    assertThatThrownBy(() -> listener.onContentCreated(event))
        .isInstanceOf(ResourceAccessException.class);

    verify(reportRepository, never()).save(any());
    verify(moderationLogRepository, never()).save(any());
    verifyNoInteractions(eventPublisher);
  }

  // ── Message events ───────────────────────────────────────────────────────

  @Test
  @DisplayName("Toxic chat message: auto-banned under its Mongo id as a MESSAGE target")
  void toxicMessageIsAutoBanned() {
    givenVerdict(true, "harassment");
    givenNotYetModerated();

    listener.onMessageCreated(
        new MessageCreatedEvent("65f1c0ffee0000000000dead", 3L, 11L, "go away"));

    ArgumentCaptor<Report> report = ArgumentCaptor.forClass(Report.class);
    verify(reportRepository).save(report.capture());
    assertThat(report.getValue().getTargetType()).isEqualTo(TargetType.MESSAGE);
    assertThat(report.getValue().getTargetId()).isEqualTo("65f1c0ffee0000000000dead");
    assertThat(report.getValue().getTargetUserId()).isEqualTo(11L);

    ModerationActionEvent action = capturePublishedAction();
    assertThat(action.action()).isEqualTo(ModerationAction.BLOCK);
    assertThat(action.targetId()).isEqualTo("65f1c0ffee0000000000dead");
  }

  @Test
  @DisplayName("Clean chat message: nothing happens")
  void cleanMessageIsLeftAlone() {
    givenVerdict(false, null);

    listener.onMessageCreated(new MessageCreatedEvent("65f1c0ffee0000000000beef", 3L, 11L, "hi"));

    verify(reportRepository, never()).save(any());
    verify(moderationLogRepository, never()).save(any());
    verifyNoInteractions(eventPublisher);
  }
}
