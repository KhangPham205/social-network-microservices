package com.socialnetwork.media_service.listener;
import com.socialnetwork.common.vo.TargetType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialnetwork.media_service.client.AiServiceClient;
import com.socialnetwork.media_service.repository.neo4j.PostNodeRepository;
import com.socialnetwork.media_service.service.MilvusService;
import com.socialnetwork.common.events.ContentCreatedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostEventSyncListener {

  private final ObjectMapper objectMapper;
  private final AiServiceClient aiServiceClient;
  private final MilvusService milvusService;
  private final PostNodeRepository postNodeRepository;

  @KafkaListener(topics = "content-created-topic", groupId = "recommendation-group")
  public void handlePostCreated(String payload) {
    try {
      // If the payload is double-serialized as a JSON string, unescape it
      if (payload != null && payload.startsWith("\"") && payload.endsWith("\"")) {
        payload = objectMapper.readValue(payload, String.class);
      }

      ContentCreatedEvent event = objectMapper.readValue(payload, ContentCreatedEvent.class);
      if (event.targetType() != TargetType.POST) {
        return; // Only sync POST events
      }

      log.info("Received new post event for targetId: {}", event.targetId());

      // 1. Get Embedding from AI Service
      List<Float> embedding = aiServiceClient.getEmbedding(event.content());

      if (!embedding.isEmpty()) {
        // 2. Insert to Milvus
        milvusService.insertPost(event.targetId(), embedding);
      }

      // 3. Insert to Neo4j
      postNodeRepository.createPostAndAuthorRelationship(event.targetId(), event.authorId());

      log.info("Successfully synced post {} to Milvus and Neo4j", event.targetId());
    } catch (Exception e) {
      log.error("Error syncing post to vector/graph DB", e);
    }
  }
}
