package com.socialnetwork.media_service.client;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class AiServiceClient {

  private final RestClient.Builder restClientBuilder;

  public List<Float> getEmbedding(String text) {
    RestClient restClient = restClientBuilder.build();

    AiResponse response =
        restClient
            .post()
            .uri("http://localhost:8000/embed")
            .body(Map.of("text", text))
            .retrieve()
            .body(AiResponse.class);

    if (response != null && response.getEmbedding() != null) {
      return response.getEmbedding();
    }
    return List.of();
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AiResponse {
    private List<Float> vector;
    private Integer dimension;

    public List<Float> getEmbedding() {
      return vector;
    }
  }
}
