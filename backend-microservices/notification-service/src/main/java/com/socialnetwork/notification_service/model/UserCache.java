package com.socialnetwork.notification_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Denormalised copy of a user's public identity; the id is the auth accountId (assigned). */
@Entity
@Table(name = "user_caches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCache {

  @Id private Long id;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "avatar_url")
  private String avatarUrl;
}
