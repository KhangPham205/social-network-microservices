package com.socialnetwork.media_service.client;

import com.socialnetwork.media_service.config.AiServiceProperties;
import com.socialnetwork.media_service.config.RestClientConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin client over the Python embedding service. Embeddings only feed the recommendation ranking,
 * so every failure degrades to an empty vector instead of propagating: callers fall back to the
 * chronological feed.
 */
@Slf4j
@Component
public class AiServiceClient {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

  private final RestClient restClient;

  public AiServiceClient(RestClient.Builder builder, AiServiceProperties properties) {
    this.restClient =
        builder
            .clone()
            .baseUrl(properties.url())
            .requestFactory(RestClientConfig.requestFactory(CONNECT_TIMEOUT, READ_TIMEOUT))
            .build();
  }

  /** Returns the embedding of {@code text}, or an empty list when ai-service cannot answer. */
  public List<Float> getEmbedding(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    try {
      EmbeddingResponse response =
          restClient
              .post()
              .uri("/embed")
              .body(Map.of("text", text))
              .retrieve()
              .body(EmbeddingResponse.class);
      if (response == null || response.vector() == null) {
        return List.of();
      }
      return response.vector();
    } catch (Exception e) {
      log.warn("ai-service embedding call failed, continuing without it: {}", e.toString());
      return List.of();
    }
  }

  /** Response body of {@code POST /embed}. */
  public record EmbeddingResponse(List<Float> vector, Integer dimension) {}
}
