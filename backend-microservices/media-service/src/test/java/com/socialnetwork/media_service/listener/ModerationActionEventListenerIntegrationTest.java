package com.socialnetwork.media_service.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialnetwork.media_service.repository.neo4j.PostNodeRepository;
import com.socialnetwork.media_service.service.CommentService;
import com.socialnetwork.media_service.service.PostService;
import com.socialnetwork.media_service.service.RecommendationService;
import com.socialnetwork.media_service.service.StorageService;
import events.ModerationActionEvent;
import io.github.perplexhub.rsql.RSQLJPAAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.neo4j.autoconfigure.DataNeo4jAutoConfiguration;
import org.springframework.boot.data.neo4j.autoconfigure.DataNeo4jRepositoriesAutoConfiguration;
import org.springframework.boot.neo4j.autoconfigure.Neo4jAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test for {@link ModerationActionEventListener} using an embedded Kafka broker.
 *
 * <p>Strategy:
 *
 * <ol>
 *   <li>Spin up an in-process Kafka broker via {@code @EmbeddedKafka}.
 *   <li>Use {@link KafkaTemplate} (auto-configured) to publish a raw {@link ModerationActionEvent}
 *       JSON message to the {@code moderation-actions} topic.
 *   <li>Mock both {@link com.socialnetwork.media_service.service.PostService} and {@link
 *       com.socialnetwork.media_service.service.CommentService}.
 *   <li>Assert that the listener routes the event correctly and calls the right service method.
 * </ol>
 *
 * <p>Neo4j auto-configuration is excluded so the test does not require a running Neo4j instance.
 */
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"moderation-actions"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(
    properties = {
      // Point the listener to the embedded broker
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "spring.kafka.consumer.group-id=media-service-group-v2",
      // Disable Eureka / Cloud Discovery so the context starts without a registry
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      // Disable R2DBC / JPA / datasource auto-config that needs a real DB
      "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      // Prevent Hibernate from creating Postgres-specific types (e.g. jsonb) in the H2 test DB
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      // Disable MinIO / Milvus starters
      "minio.enabled=false",
      "milvus.enabled=false",
        // Init value
        "minio.url=http://localhost:9000",
        "minio.access.name=dummy-access",
        "minio.access.secret=dummy-secret",
        "minio.bucket-name=dummy-bucket",
        "minio.access-key=dummy-access",
        "minio.secret-key=dummy-secret",
        "jwt.secret=ThisIsADummySecretKeyForTestingPurposesOnlySoItNeedsToBeLong",
        "jwt.expiration=86400000",
        "app.minio.public-url=http://localhost:9000/dummy-bucket"
    })
@EnableAutoConfiguration(
    exclude = {
      Neo4jAutoConfiguration.class,
      DataNeo4jAutoConfiguration.class,
      DataNeo4jRepositoriesAutoConfiguration.class,
      // Disable RSQL JPA auto-configuration for tests to avoid requiring additional dialect classes
      RSQLJPAAutoConfiguration.class
    })
@ActiveProfiles("test")
@DirtiesContext
class ModerationActionEventListenerIntegrationTest {

  private static final String MODERATION_ACTIONS_TOPIC = "moderation-actions";

  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

  // ── Service mocks – the listener Spring bean will receive these ──
  @MockitoBean private PostService postService;
  @MockitoBean private CommentService commentService;
  @MockitoBean private RecommendationService recommendationService;
  @MockitoBean private PostNodeRepository postNodeRepository;
  @MockitoBean private Driver neo4jDriver;
  // Mock beans that would otherwise attempt to initialize external resources
  @MockitoBean private DatabaseSelectionProvider databaseSelectionProvider;
  @MockitoBean private StorageService storageService;

  // Jackson mapper used to build the payload
  // Yêu cầu Spring tiêm ObjectMapper xịn vào
  @Autowired
  private ObjectMapper objectMapper;


  private boolean isConsumerReady = false;
  // ─────────────────────────────────────────────────────────────────
  //  Helper: produce a JSON payload and wait for the listener to consume it
  // ─────────────────────────────────────────────────────────────────

  /**
   * Publishes a {@link ModerationActionEvent} as JSON to the embedded Kafka topic and returns after
   * giving the async listener time to process it.
   */
  private void publishAndAwait(ModerationActionEvent event) throws Exception {
    if (!isConsumerReady) {
      System.out.println("⏳ Đợi 3 giây cho Kafka Consumer khởi động và join group...");
      Thread.sleep(3000);
      isConsumerReady = true;
    }

    kafkaTemplate.send(MODERATION_ACTIONS_TOPIC, event.getTargetId(), event).get();
    // Give the async consumer up to 5 seconds to process the record
    Thread.sleep(2_000);
  }

  // ─────────────────────────────────────────────────────────────────
  //  Tests
  // ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("BLOCK event for a POST → postService.updateSystemBanStatus(id, true) is called")
  void whenBlockPostEvent_thenPostServiceBansPost() throws Exception {
    // --- Arrange ---
    Long postId = 1001L;
    ModerationActionEvent blockEvent =
        ModerationActionEvent.builder()
            .targetId(postId.toString())
            .targetType("POST")
            .action("BLOCK")
            .build();

    // --- Act ---
    publishAndAwait(blockEvent);

    // --- Assert ---
    ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Boolean> banCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(postService, timeout(10_000).times(1))
        .updateSystemBanStatus(idCaptor.capture(), banCaptor.capture());

    assertThat(idCaptor.getValue()).isEqualTo(postId);
    assertThat(banCaptor.getValue()).isTrue(); // isBanned = true for BLOCK

    // CommentService must not be touched
    verify(commentService, never()).updateSystemBanStatus(any(), anyBoolean());
  }

  @Test
  @DisplayName(
      "BLOCK event for a COMMENT → commentService.updateSystemBanStatus(id, true) is called")
  void whenBlockCommentEvent_thenCommentServiceBansComment() throws Exception {
    // --- Arrange ---
    Long commentId = 2002L;
    ModerationActionEvent blockEvent =
        ModerationActionEvent.builder()
            .targetId(commentId.toString())
            .targetType("COMMENT")
            .action("BLOCK")
            .build();

    // --- Act ---
    publishAndAwait(blockEvent);

    // --- Assert ---
    ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Boolean> banCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(commentService, timeout(5_000).times(1))
        .updateSystemBanStatus(idCaptor.capture(), banCaptor.capture());

    assertThat(idCaptor.getValue()).isEqualTo(commentId);
    assertThat(banCaptor.getValue()).isTrue();

    verify(postService, never()).updateSystemBanStatus(any(), anyBoolean());
  }

  @Test
  @DisplayName("UNBLOCK event for a POST → postService.updateSystemBanStatus(id, false) is called")
  void whenUnblockPostEvent_thenPostServiceUnbansPost() throws Exception {
    // --- Arrange ---
    Long postId = 3003L;
    ModerationActionEvent unblockEvent =
        ModerationActionEvent.builder()
            .targetId(postId.toString())
            .targetType("POST")
            .action("UNBLOCK")
            .build();

    // --- Act ---
    publishAndAwait(unblockEvent);

    // --- Assert ---
    ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Boolean> banCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(postService, timeout(5_000).times(1))
        .updateSystemBanStatus(idCaptor.capture(), banCaptor.capture());

    assertThat(idCaptor.getValue()).isEqualTo(postId);
    assertThat(banCaptor.getValue()).isFalse(); // isBanned = false for UNBLOCK
  }

  @Test
  @DisplayName("Event for an unsupported TargetType (e.g. USER) → neither service is called")
  void whenUnsupportedTargetType_thenNoServiceIsCalled() throws Exception {
    // --- Arrange ---
    ModerationActionEvent userEvent =
        ModerationActionEvent.builder().targetId("9").targetType("USER").action("BLOCK").build();

    // --- Act ---
    publishAndAwait(userEvent);

    // --- Assert ---
    verify(postService, never()).updateSystemBanStatus(any(), anyBoolean());
    verify(commentService, never()).updateSystemBanStatus(any(), anyBoolean());
  }
}
