package com.socialnetwork.media_service.service;

import com.socialnetwork.media_service.client.AiServiceClient;
import com.socialnetwork.media_service.dto.post.PostSyncDto;
import com.socialnetwork.media_service.repository.neo4j.PostNodeRepository;
import com.socialnetwork.media_service.repository.neo4j.SocialScoreProjection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationService {

  private final MilvusService milvusService;
  private final PostNodeRepository postNodeRepository;
  private final AiServiceClient aiServiceClient;
  private final com.socialnetwork.media_service.repository.PostRepository postRepository;

  private static final double ALPHA = 0.6; // Milvus Contextual Weight
  private static final double BETA = 0.4; // Neo4j Social Weight

  public void syncAllPostsFromDb() {
    log.info("Starting batch sync of all posts from PostgreSQL to Milvus/Neo4j...");
    List<com.socialnetwork.media_service.model.Post> allPosts = postRepository.findAll();

    List<PostSyncDto> syncDtos =
        allPosts.stream()
            .filter(
                p ->
                    p.getAccessModifier()
                            == com.socialnetwork.media_service.enums.AccessScope.PUBLIC
                        || p.getAccessModifier()
                            == com.socialnetwork.media_service.enums.AccessScope.FRIENDS)
            .map(p -> new PostSyncDto(p.getId(), p.getContent(), p.getAuthor().getId()))
            .collect(Collectors.toList());

    syncPosts(syncDtos);
  }

  public void syncPosts(List<PostSyncDto> posts) {
    log.info("Starting batch sync for {} posts...", posts.size());
    int successCount = 0;
    int failCount = 0;

    for (PostSyncDto post : posts) {
      try {
        // 1. Get Embedding
        List<Float> embedding =
            aiServiceClient.getEmbedding(post.getContent() != null ? post.getContent() : "");

        if (!embedding.isEmpty()) {
          // 2. Insert to Milvus
          milvusService.insertPost(post.getId(), embedding);
        }

        // 3. Insert to Neo4j
        postNodeRepository.createPostAndAuthorRelationship(post.getId(), post.getAuthorId());
        successCount++;
      } catch (Exception e) {
        log.error("Failed to sync post {}", post.getId(), e);
        failCount++;
      }
    }

    log.info("Sync completed. Success: {}, Failed: {}", successCount, failCount);
  }

  public List<Long> getExploreFeed(Long currentUserId, String filter) {
    // 1. Get User Profile Embedding (Generic or from semantic search filter)
    String searchPrompt =
        (filter != null && !filter.isBlank())
            ? filter
            : "generic user interests regarding daily life and technology";
    List<Float> userEmbedding = aiServiceClient.getEmbedding(searchPrompt);

    if (userEmbedding.isEmpty()) {
      return List.of();
    }

    // 2. Contextual Score (Milvus) - Get Top 100
    List<MilvusService.MilvusSearchResult> milvusResults =
        milvusService.searchSimilarPosts(userEmbedding, 100);
    if (milvusResults.isEmpty()) {
      return List.of();
    }

    List<Long> candidatePostIds =
        milvusResults.stream().map(MilvusService.MilvusSearchResult::postId).toList();

    // 3. Social Score (Neo4j)
    List<SocialScoreProjection> neo4jResults =
        postNodeRepository.getSocialScores(currentUserId, candidatePostIds);
    Map<Long, Long> socialScoreMap =
        neo4jResults.stream()
            .collect(
                Collectors.toMap(
                    SocialScoreProjection::getPostId, SocialScoreProjection::getSocialScore));

    // 4. Normalize and Hybrid Re-ranking
    double maxDistance =
        milvusResults.stream()
            .mapToDouble(MilvusService.MilvusSearchResult::distance)
            .max()
            .orElse(1.0);
    double maxSocialScore =
        neo4jResults.stream().mapToDouble(SocialScoreProjection::getSocialScore).max().orElse(1.0);

    if (maxDistance <= 0) maxDistance = 1.0;
    if (maxSocialScore <= 0) maxSocialScore = 1.0;

    double finalMaxDistance = maxDistance;
    double finalMaxSocialScore = maxSocialScore;

    record RankedPost(Long postId, double finalScore) {}

    List<Long> finalRecommendations =
        milvusResults.stream()
            .map(
                m -> {
                  long socialScore = socialScoreMap.getOrDefault(m.postId(), 0L);

                  // Min-Max Normalization (assuming 0 is min)
                  double normalizedContext = m.distance() / finalMaxDistance;
                  double normalizedSocial = socialScore / finalMaxSocialScore;

                  double finalScore = (ALPHA * normalizedContext) + (BETA * normalizedSocial);
                  return new RankedPost(m.postId(), finalScore);
                })
            .sorted(Comparator.comparingDouble(RankedPost::finalScore).reversed()) // Sort DESC
            .map(RankedPost::postId)
            .toList();

    return finalRecommendations;
  }
}
