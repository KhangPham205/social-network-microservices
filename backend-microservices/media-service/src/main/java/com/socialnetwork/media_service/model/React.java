package com.socialnetwork.media_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import com.socialnetwork.common.vo.TargetType;

@Entity
@Table(
    name = "reacts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "target_id", "target_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class React {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Read model of the reacting user. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserCache user;

  @Column(name = "target_id", nullable = false)
  private Long targetId;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false)
  private TargetType targetType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reaction_type_id", nullable = false)
  private ReactType reactType;

  @CreationTimestamp private Instant createdAt;
}
