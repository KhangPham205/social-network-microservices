package com.socialnetwork.moderation_service.model;

import com.socialnetwork.common.entity.BaseEntity;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.enums.ReportReason;
import com.socialnetwork.moderation_service.enums.ReportSource;
import com.socialnetwork.moderation_service.enums.ReportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * One report against a post, comment, message or user. A report is either filed by a user
 * ({@code source = USER}, {@code reporterId} set) or by the AI moderation pipeline
 * ({@code source = SYSTEM}, {@code reporterId} null).
 */
@Entity
@Table(name = "reports")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Report extends BaseEntity {

  /** Null for reports raised by the system. */
  @Column(name = "reporter_id")
  private Long reporterId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private ReportSource source = ReportSource.USER;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private ReportStatus status = ReportStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TargetType targetType;

  @Column(nullable = false)
  private String targetId;

  /** Owner of the reported content. */
  @Column(name = "target_user_id", nullable = false)
  private Long targetUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReportReason reason;

  private String customReason;

  @Column(name = "is_banned_by_system", nullable = false)
  @Builder.Default
  private boolean bannedBySystem = false;
}
