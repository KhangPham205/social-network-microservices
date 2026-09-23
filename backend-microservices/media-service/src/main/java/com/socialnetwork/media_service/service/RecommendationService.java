package com.socialnetwork.media_service.service;

import com.socialnetwork.media_service.client.AiServiceClient;
import com.socialnetwork.media_service.enums.AccessScope;
import com.socialnetwork.media_service.model.Post;
import com.socialnetwork.media_service.repository.PostRepository;
import com.socialnetwork.media_service.repository.neo4j.PostNodeRepository;
import com.socialnetwork.media_service.repository.neo4j.SocialScoreProjection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hybrid recommendation: a semantic candidate set from Milvus re-ranked with a social score from
 * Neo4j. Every backing store is optional at runtime; when one is missing the explore feed returns
 * an empty candidate list and {@code PostService} falls back to a chronological feed.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationService {

  /** Weight of the semantic (Milvus) score. */
  private static final double ALPHA = 0.6;

  /** Weight of the social (Neo4j) score. */
  private static final double BETA = 0.4;

  private static final int CANDIDATE_LIMIT = 100;
  private static final String DEFAULT_PROMPT =
      "generic user interests regarding daily life and technology";

  private final MilvusService milvusService;
  private final PostNodeRepository postNodeRepository;
  private final AiServiceClient aiServiceClient;
  private final PostRepository postRepository;

  /** Indexes one post; private, deleted and banned posts are never indexed. */
  @Transactional(readOnly = true)
  public void indexPost(Long postId) {
    Post post = postRepository.findByIdAndDeletedAtIsNull(postId).orElse(null);
    if (post == null) {
      log.debug("Post {} is gone, nothing to index", postId);
      return;
    }
    index(post);
  }

  /** Rebuilds the whole index from PostgreSQL; used by the admin resync endpoint. */
  @Transactional(readOnly = true)
  public int syncAllPostsFromDb() {
    List<Post> posts = postRepository.findAll();
    int indexed = 0;
    int failed = 0;
    for (Post post : posts) {
      if (post.getDeletedAt() != null || post.getAccessModifier() == AccessScope.PRIVATE) {
        continue;
      }
      try {
        index(post);
        indexed++;
      } catch (Exception e) {
        failed++;
        log.error("Failed to index post {}", post.getId(), e);
      }
    }
    log.info("Recommendation resync finished: {} indexed, {} failed", indexed, failed);
    return indexed;
  }

  private void index(Post post) {
    if (post.getAccessModifier() == AccessScope.PRIVATE) {
      log.debug("Post {} is private, not indexing it", post.getId());
      return;
    }
    List<Float> embedding = aiServiceClient.getEmbedding(post.getContent());
    if (!embedding.isEmpty()) {
      milvusService.insertPost(post.getId(), embedding);
    }
    postNodeRepository.createPostAndAuthorRelationship(post.getId(), post.getAuthor().getId());
    log.debug("Indexed post {}", post.getId());
  }

  /**
   * Post ids ranked for {@code viewerId}, best first. Empty when the AI service or Milvus cannot
   * answer; the caller then serves a chronological feed.
   */
  public List<Long> getExploreFeed(Long viewerId, String filter) {
    String searchPrompt = (filter != null && !filter.isBlank()) ? filter : DEFAULT_PROMPT;
    List<Float> userEmbedding = aiServiceClient.getEmbedding(searchPrompt);
    if (userEmbedding.isEmpty()) {
      return List.of();
    }

    List<MilvusService.MilvusSearchResult> candidates =
        milvusService.searchSimilarPosts(userEmbedding, CANDIDATE_LIMIT);
    if (candidates.isEmpty()) {
      return List.of();
    }

    List<Long> candidateIds =
        candidates.stream().map(MilvusService.MilvusSearchResult::postId).toList();
    Map<Long, Long> socialScores = socialScores(viewerId, candidateIds);

    double maxDistance =
        Math.max(
            candidates.stream()
                .mapToDouble(MilvusService.MilvusSearchResult::distance)
                .max()
                .orElse(1.0),
            Double.MIN_NORMAL);
    double maxSocialScore =
        Math.max(socialScores.values().stream().mapToDouble(Long::doubleValue).max().orElse(1.0),
            Double.MIN_NORMAL);

    record RankedPost(Long postId, double score) {}

    return candidates.stream()
        .map(
            candidate -> {
              double semantic = candidate.distance() / maxDistance;
              double social = socialScores.getOrDefault(candidate.postId(), 0L) / maxSocialScore;
              return new RankedPost(candidate.postId(), ALPHA * semantic + BETA * social);
            })
        .sorted(Comparator.comparingDouble(RankedPost::score).reversed())
        .map(RankedPost::postId)
        .toList();
  }

  /** Social scores from Neo4j; an outage only costs the ranking its social component. */
  private Map<Long, Long> socialScores(Long viewerId, List<Long> candidateIds) {
    try {
      return postNodeRepository.getSocialScores(viewerId, candidateIds).stream()
          .collect(
              Collectors.toMap(
                  SocialScoreProjection::getPostId,
                  SocialScoreProjection::getSocialScore,
                  (a, b) -> a));
    } catch (Exception e) {
      log.warn("Neo4j social scores unavailable, ranking on semantics only: {}", e.toString());
      return Map.of();
    }
  }
}
