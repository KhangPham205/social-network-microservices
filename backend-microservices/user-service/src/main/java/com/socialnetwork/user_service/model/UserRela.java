package com.socialnetwork.user_service.model;

import jakarta.persistence.*;
import jakarta.persistence.Id;
import lombok.*;
import lombok.experimental.*;

@Entity
@Table(name = "user_rela")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserRela {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "follower", nullable = false)
  private User follower; // người theo dõi

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "following", nullable = false)
  private User following; // người được theo dõi
}
