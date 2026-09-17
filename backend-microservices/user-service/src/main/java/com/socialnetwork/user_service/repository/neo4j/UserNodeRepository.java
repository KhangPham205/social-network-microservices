package com.socialnetwork.user_service.repository.neo4j;

import com.socialnetwork.user_service.model.node.UserNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UserNodeRepository extends Neo4jRepository<UserNode, Long> {

  @Query(
      value =
          "MATCH (me:UserNode {id: $userId})-[:FRIENDS_WITH]-(friend)-[:FRIENDS_WITH]-(fof:UserNode) "
              + "WHERE NOT (me)-[:FRIENDS_WITH]-(fof) AND me <> fof "
              + "RETURN fof.id AS id, fof.displayName AS displayName, count(friend) AS mutualFriendsCount "
              + "ORDER BY mutualFriendsCount DESC SKIP $skip LIMIT $limit",
      countQuery =
          "MATCH (me:UserNode {id: $userId})-[:FRIENDS_WITH]-(friend)-[:FRIENDS_WITH]-(fof:UserNode) "
              + "WHERE NOT (me)-[:FRIENDS_WITH]-(fof) AND me <> fof "
              + "RETURN count(DISTINCT fof)")
  Page<RecommendedFriendProjection> findFriendRecommendations(
      @Param("userId") Long userId, Pageable pageable);

  @Transactional("neo4jTransactionManager")
  @Query(
      "MATCH (u1:UserNode {id: $userId1}), (u2:UserNode {id: $userId2}) MERGE (u1)-[:FRIENDS_WITH]->(u2)")
  void createFriendship(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

  @Transactional("neo4jTransactionManager")
  @Query(
      "MATCH (u1:UserNode {id: $userId1})-[r:FRIENDS_WITH]-(u2:UserNode {id: $userId2}) DELETE r")
  void deleteFriendship(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
