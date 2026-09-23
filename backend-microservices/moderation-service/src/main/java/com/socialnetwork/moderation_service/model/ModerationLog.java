package com.socialnetwork.moderation_service.model;

import com.socialnetwork.common.entity.BaseEntity;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.enums.ModerationLogAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Audit trail: exactly one row per moderation action taken by this service. */
@Entity
@Table(name = "moderation_logs")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationLog extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TargetType targetType;

  @Column(nullable = false)
  private String targetId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ModerationLogAction action;

  private String reason;

  /** Administrator who performed the action; null for automated decisions. */
  @Column(name = "actor_id")
  private Long actorId;

  /** The report this action resolves, when there is one. */
  @Column(name = "report_id")
  private Long reportId;

  /** The complaint this action resolves, when there is one. */
  @Column(name = "complaint_id")
  private Long complaintId;
}
