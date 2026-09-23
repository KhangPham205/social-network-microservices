package com.socialnetwork.media_service.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.ModerationActionEvent;
import com.socialnetwork.common.vo.ModerationAction;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.media_service.client.AiServiceClient;
import com.socialnetwork.media_service.repository.neo4j.PostNodeRepository;
import com.socialnetwork.media_service.service.CommentService;
import com.socialnetwork.media_service.service.PostService;
import io.milvus.client.MilvusServiceClient;
import io.minio.MinioClient;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Round trip over a real (in-process) broker: a typed {@link ModerationActionEvent} is produced on
 * {@code moderation-actions} and the listener must route it to the right service. Everything that
 * would reach out to Milvus, Neo4j, MinIO or ai-service is mocked, and the database is H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = {
      KafkaTopics.MODERATION_ACTIONS,
      KafkaTopics.CONTENT_CREATED,
      KafkaTopics.USER_CREATED,
      KafkaTopics.PROFILE_UPDATED,
      KafkaTopics.NOTIFICATION
    },
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=true")
@DirtiesContext
class ModerationActionEventListenerIntegrationTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(15);

  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
  @Autowired private KafkaListenerEndpointRegistry endpointRegistry;

  @MockitoBean private PostService postService;
  @MockitoBean private CommentService commentService;
  @MockitoBean private MilvusServiceClient milvusServiceClient;
  @MockitoBean private Driver neo4jDriver;
  @MockitoBean private MinioClient minioClient;
  @MockitoBean private AiServiceClient aiServiceClient;
  @MockitoBean private PostNodeRepository postNodeRepository;

  @BeforeEach
  void waitForConsumerAssignment() {
    for (MessageListenerContainer container : endpointRegistry.getListenerContainers()) {
      String[] topics = container.getContainerProperties().getTopics();
      if (topics != null && java.util.Arrays.asList(topics).contains(KafkaTopics.MODERATION_ACTIONS)) {
        ContainerTestUtils.waitForAssignment(container, 1);
      }
    }
  }

  private void publish(ModerationActionEvent event) throws Exception {
    kafkaTemplate.send(KafkaTopics.MODERATION_ACTIONS, event.targetId(), event).get();
  }

  @Test
  @DisplayName("BLOCK on a POST bans the post and leaves comments alone")
  void blockPostEventBansPost() throws Exception {
    Long postId = 1001L;
    publish(
        ModerationActionEvent.builder()
            .targetId(postId.toString())
            .targetType(TargetType.POST)
            .action(ModerationAction.BLOCK)
            .build());

    ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Boolean> banCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(postService, timeout(TIMEOUT.toMillis()))
        .updateSystemBanStatus(idCaptor.capture(), banCaptor.capture());

    assertThat(idCaptor.getValue()).isEqualTo(postId);
    assertThat(banCaptor.getValue()).isTrue();
    verify(commentService, never()).updateSystemBanStatus(any(), anyBoolean());
  }

  @Test
  @DisplayName("BLOCK on a COMMENT bans the comment and leaves posts alone")
  void blockCommentEventBansComment() throws Exception {
    Long commentId = 2002L;
    publish(
        ModerationActionEvent.builder()
            .targetId(commentId.toString())
            .targetType(TargetType.COMMENT)
            .action(ModerationAction.BLOCK)
            .build());

    ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Boolean> banCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(commentService, timeout(TIMEOUT.toMillis()))
        .updateSystemBanStatus(idCaptor.capture(), banCaptor.capture());

    assertThat(idCaptor.getValue()).isEqualTo(commentId);
    assertThat(banCaptor.getValue()).isTrue();
    verify(postService, never()).updateSystemBanStatus(any(), anyBoolean());
  }

  @Test
  @DisplayName("UNBLOCK on a POST lifts the ban")
  void unblockPostEventUnbansPost() throws Exception {
    Long postId = 3003L;
    publish(
        ModerationActionEvent.builder()
            .targetId(postId.toString())
            .targetType(TargetType.POST)
            .action(ModerationAction.UNBLOCK)
            .build());

    ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Boolean> banCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(postService, timeout(TIMEOUT.toMillis()))
        .updateSystemBanStatus(idCaptor.capture(), banCaptor.capture());

    assertThat(idCaptor.getValue()).isEqualTo(postId);
    assertThat(banCaptor.getValue()).isFalse();
  }
}
