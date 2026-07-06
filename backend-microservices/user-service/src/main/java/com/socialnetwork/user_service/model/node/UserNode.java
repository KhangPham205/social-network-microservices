package com.socialnetwork.user_service.model.node;

import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

@Node
@Data
@NoArgsConstructor
public class UserNode {

  @Id private Long id;

  private String displayName;

  @Relationship(type = "FRIENDS_WITH", direction = Relationship.Direction.OUTGOING)
  private Set<UserNode> friends = new HashSet<>();
}
