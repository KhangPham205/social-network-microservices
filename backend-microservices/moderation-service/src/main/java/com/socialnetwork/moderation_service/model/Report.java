package com.socialnetwork.moderation_service.model;

import com.socialnetwork.moderation_service.enums.ReportReason;
import com.socialnetwork.moderation_service.enums.ReportStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import vo.TargetType;

@Entity
@Table(name = "reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Store reporter ID instead of entity reference
  @Column(name = "reporter_id", nullable = false)
  private Long reporterId;

  @Column(name = "status")
  private ReportStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TargetType targetType; // POST, COMMENT, MESSAGE, USER

  @Column(nullable = false)
  private String targetId; // ID of reported content

  @Column(name = "target_user_id", nullable = false)
  private Long targetUserId; // ID of content owner

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReportReason reason;

  private String customReason;

  private Boolean isBannedBySystem = false;

  @CreationTimestamp
  @Column(updatable = false)
  private Instant createdAt;
}
