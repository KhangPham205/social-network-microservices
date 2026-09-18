package com.socialnetwork.moderation_service.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.socialnetwork.moderation_service.client.AiServiceClient;
import com.socialnetwork.moderation_service.dto.AiModerationRequest;
import com.socialnetwork.moderation_service.dto.AiModerationResponse;
import com.socialnetwork.moderation_service.event.ContentCreatedEvent;
import com.socialnetwork.moderation_service.model.ModerationLog;
import com.socialnetwork.moderation_service.model.Report;
import com.socialnetwork.moderation_service.repository.ModerationLogRepository;
import com.socialnetwork.moderation_service.repository.ReportRepository;
import com.socialnetwork.moderation_service.service.ModerationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import com.socialnetwork.common.vo.TargetType;

/**
 * Unit tests for {@link ContentModerationListener}.
 *
 * <p>Strategy: isolate the listener from Kafka infrastructure entirely. We deserialise the JSON
 * payload manually (mirroring what the real KafkaListener deserialiser does) and inject it directly
 * into the method under test.
 */
@ExtendWith(MockitoExtension.class)
class ContentModerationListenerTest {

  // ── Production ObjectMapper (no Spring context needed; Jackson is available on classpath) ──
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private ModerationLogRepository moderationLogRepository;
  @Mock private ReportRepository reportRepository;
  @Mock private ModerationService moderationService;
  @Mock private AiServiceClient aiServiceClient;

  @InjectMocks private ContentModerationListener listener;

  /**
   * Wire the real ObjectMapper into the listener. Mockito @InjectMocks doesn't inject the
   * already-constructed ObjectMapper above, so we set it via reflection-friendly Mockito spy
   * constructor or simply rebuild the listener with the constructor.
   */
  @BeforeEach
  void setUp() {
    listener =
        new ContentModerationListener(
            moderationLogRepository,
            reportRepository,
            moderationService,
            objectMapper,
            aiServiceClient);
  }

  // ─────────────────────────────────────────────────────────────────
  //  Helper – build a ContentCreatedEvent JSON payload
  // ─────────────────────────────────────────────────────────────────
  private String buildPayload(Long targetId, TargetType type, String content, Long authorId)
      throws Exception {
    ContentCreatedEvent event = new ContentCreatedEvent(targetId, type, content, authorId, null);
    return objectMapper.writeValueAsString(event);
  }

  // ─────────────────────────────────────────────────────────────────
  //  Tests
  // ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Given a TOXIC AI response, blockContent is called and a ModerationLog is saved")
  void whenAiResponseIsToxic_thenBlockContentAndSaveModerationLog() throws Exception {
    // --- Arrange ---
    Long postId = 42L;
    String toxicText = "This is hate speech content";
    String payload = buildPayload(postId, TargetType.POST, toxicText, 7L);

    AiModerationResponse toxicResponse = new AiModerationResponse(true, 0.97, "hate_speech");

    when(aiServiceClient.checkToxicity(any(AiModerationRequest.class))).thenReturn(toxicResponse);

    // Report doesn't exist yet → system creates one
    when(reportRepository.existsByTargetIdAndTargetTypeAndIsBannedBySystemIsNotNull(
            anyString(), any(TargetType.class)))
        .thenReturn(false);

    // --- Act ---
    listener.handleContentCreationViaKafka(payload);

    // --- Assert: AI client received the correct text ---
    ArgumentCaptor<AiModerationRequest> requestCaptor =
        ArgumentCaptor.forClass(AiModerationRequest.class);
    verify(aiServiceClient, times(1)).checkToxicity(requestCaptor.capture());
    assertThat(requestCaptor.getValue().getText()).isEqualTo(toxicText);

    // --- Assert: content is blocked in moderation service ---
    verify(moderationService, times(1)).blockContent(eq(postId.toString()), eq(TargetType.POST));

    // --- Assert: a Report entity is persisted ---
    ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
    verify(reportRepository, times(1)).save(reportCaptor.capture());
    Report savedReport = reportCaptor.getValue();
    assertThat(savedReport.getTargetId()).isEqualTo(postId.toString());
    assertThat(savedReport.getTargetType()).isEqualTo(TargetType.POST);
    assertThat(savedReport.getIsBannedBySystem()).isTrue();

    // --- Assert: a ModerationLog entry is persisted ---
    ArgumentCaptor<ModerationLog> logCaptor = ArgumentCaptor.forClass(ModerationLog.class);
    verify(moderationLogRepository, times(1)).save(logCaptor.capture());
    ModerationLog savedLog = logCaptor.getValue();
    assertThat(savedLog.getTargetType()).isEqualTo(TargetType.POST);
    assertThat(savedLog.getTargetId()).isEqualTo(postId.toString());
    assertThat(savedLog.getAction()).isEqualTo("AUTO_BAN");
    assertThat(savedLog.getReason()).contains("hate_speech");
  }

  @Test
  @DisplayName("Given a CLEAN AI response, blockContent is NOT called and nothing is persisted")
  void whenAiResponseIsClean_thenNoBlockAndNoPersistence() throws Exception {
    // --- Arrange ---
    Long postId = 99L;
    String payload = buildPayload(postId, TargetType.POST, "A wholesome family post", 3L);

    AiModerationResponse cleanResponse = new AiModerationResponse(false, 0.05, null);
    when(aiServiceClient.checkToxicity(any(AiModerationRequest.class))).thenReturn(cleanResponse);

    // --- Act ---
    listener.handleContentCreationViaKafka(payload);

    // --- Assert ---
    verify(moderationService, never()).blockContent(any(), any());
    verify(reportRepository, never()).save(any());
    verify(moderationLogRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "Given a COMMENT type TOXIC event, TargetType.COMMENT is used throughout the pipeline")
  void whenToxicComment_thenCommentTypeIsPreservedInPersistence() throws Exception {
    // --- Arrange ---
    Long commentId = 7L;
    String payload = buildPayload(commentId, TargetType.COMMENT, "Abusive comment text", 5L);

    AiModerationResponse toxicResponse = new AiModerationResponse(true, 0.89, "abusive_language");

    when(aiServiceClient.checkToxicity(any())).thenReturn(toxicResponse);
    when(reportRepository.existsByTargetIdAndTargetTypeAndIsBannedBySystemIsNotNull(
            anyString(), any()))
        .thenReturn(false);

    // --- Act ---
    listener.handleContentCreationViaKafka(payload);

    // --- Assert ---
    verify(moderationService).blockContent(eq(commentId.toString()), eq(TargetType.COMMENT));

    ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
    verify(reportRepository).save(reportCaptor.capture());
    assertThat(reportCaptor.getValue().getTargetType()).isEqualTo(TargetType.COMMENT);
  }

  @Test
  @DisplayName("Given a duplicate TOXIC event, the second Report is NOT saved (idempotency guard)")
  void whenToxicContentAlreadyBanned_thenReportIsNotDuplicated() throws Exception {
    // --- Arrange ---
    Long postId = 55L;
    String payload = buildPayload(postId, TargetType.POST, "Already banned content", 9L);

    AiModerationResponse toxicResponse = new AiModerationResponse(true, 0.95, "spam");

    when(aiServiceClient.checkToxicity(any())).thenReturn(toxicResponse);
    // Simulate: a report already exists
    when(reportRepository.existsByTargetIdAndTargetTypeAndIsBannedBySystemIsNotNull(
            anyString(), any()))
        .thenReturn(true);

    // --- Act ---
    listener.handleContentCreationViaKafka(payload);

    // --- Assert: blockContent still fires (Kafka command resent), but NO new Report row ---
    verify(moderationService, times(1)).blockContent(any(), any());
    verify(reportRepository, never()).save(any());
  }

  @Test
  @DisplayName("Given a malformed JSON payload, no exception propagates and nothing is called")
  void whenMalformedPayload_thenExceptionIsSuppressedGracefully() {
    // --- Arrange ---
    String badPayload = "{ INVALID JSON !!";

    // --- Act – must not throw ---
    listener.handleContentCreationViaKafka(badPayload);

    // --- Assert ---
    verify(aiServiceClient, never()).checkToxicity(any());
    verify(moderationService, never()).blockContent(any(), any());
  }

  @Test
  @DisplayName("Given TOXIC event for a COMMENT with media, media list is forwarded to AI client")
  void whenToxicEventHasMedia_thenAiRequestIncludesMedia() throws Exception {
    // --- Arrange ---
    Long postId = 12L;
    ContentCreatedEvent event =
        new ContentCreatedEvent(
            postId,
            TargetType.POST,
            "Post with image",
            2L,
            List.of(java.util.Map.of("url", "http://img.test/1.jpg", "type", "IMAGE")));
    String payload = objectMapper.writeValueAsString(event);

    AiModerationResponse toxicResponse = new AiModerationResponse(true, 0.91, "nsfw_image");

    when(aiServiceClient.checkToxicity(any())).thenReturn(toxicResponse);
    when(reportRepository.existsByTargetIdAndTargetTypeAndIsBannedBySystemIsNotNull(
            anyString(), any()))
        .thenReturn(false);

    // --- Act ---
    listener.handleContentCreationViaKafka(payload);

    // --- Assert: AI request included the media list ---
    ArgumentCaptor<AiModerationRequest> captor = ArgumentCaptor.forClass(AiModerationRequest.class);
    verify(aiServiceClient).checkToxicity(captor.capture());
    assertThat(captor.getValue().getMedia()).isNotNull().isNotEmpty();
    assertThat(captor.getValue().getMedia().get(0)).containsEntry("url", "http://img.test/1.jpg");
  }
}
