package com.socialnetwork.media_service.model.node;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/** Graph twin of a post, used to compute the social part of the recommendation score. */
@Node("Post")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostNode {

  @Id private Long id;
}
