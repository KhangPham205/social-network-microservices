package com.socialnetwork.user_service.model.node;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

/** Graph projection of a user, used for friend-of-friend recommendations. */
@Node
@Getter
@Setter
@NoArgsConstructor
public class UserNode {

  @Id private Long id;

  private String displayName;

  @Relationship(type = "FRIENDS_WITH", direction = Relationship.Direction.OUTGOING)
  private Set<UserNode> friends = new HashSet<>();
}
