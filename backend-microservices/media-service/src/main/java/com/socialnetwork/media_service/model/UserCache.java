package com.socialnetwork.media_service.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "user_caches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCache {
  /** Same id as the user in user-service; this table is a read model, not a source. */
  @Id private Long id;

  private String displayName;
  private String avatarUrl;
}
