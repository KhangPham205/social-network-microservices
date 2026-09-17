package com.socialnetwork.user_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfo {

  @Id private Long id;

  private String bio;

  private String favorites;

  private Instant dateOfBirth;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "id")
  private User user;

  @CreationTimestamp private Instant createdAt;

  private String createdBy;

  @UpdateTimestamp private Instant updatedAt;

  private String updatedBy;
}
