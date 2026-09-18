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
  @Id private Long id; // Trùng với ID bên user-service

  private String displayName;
  private String avatarUrl;
}
