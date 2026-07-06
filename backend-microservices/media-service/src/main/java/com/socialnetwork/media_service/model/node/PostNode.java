package com.socialnetwork.media_service.model.node;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Post")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostNode {
  @Id private Long id;
}
