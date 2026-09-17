package com.socialnetwork.media_service.repository.neo4j;

import com.socialnetwork.media_service.model.node.PostNode;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PostNodeRepository extends Neo4jRepository<PostNode, Long> {

  @Query(
      "MERGE (u:UserNode {id: $authorId}) "
          + "MERGE (p:Post {id: $postId}) "
          + "MERGE (u)-[:POSTED]->(p)")
  @Transactional(value = "neo4jTransactionManager", propagation = Propagation.REQUIRES_NEW)
  void createPostAndAuthorRelationship(
      @Param("postId") Long postId, @Param("authorId") Long authorId);

  @Query(
      "MATCH (u:UserNode {id: $userId})-[*1..2]-(friend)-[:LIKED|POSTED]->(p:Post) "
          + "WHERE p.id IN $postIds "
          + "RETURN p.id as postId, count(friend) as socialScore")
  List<SocialScoreProjection> getSocialScores(
      @Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}
