package com.socialnetwork.user_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User {

  @Id private Long id;

  @Column(nullable = false)
  private String displayName;

  private String avatarUrl;

  private String interestedUser;

  private Instant lastActiveAt;

  @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
  private UserInfo userInfo;

  @CreationTimestamp private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;
}
