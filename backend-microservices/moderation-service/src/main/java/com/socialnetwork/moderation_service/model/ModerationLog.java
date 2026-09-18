package com.socialnetwork.moderation_service.model;

import com.socialnetwork.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.socialnetwork.common.vo.TargetType;

@Entity
@Table(name = "moderation_logs")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationLog extends BaseEntity {

  @Enumerated(EnumType.STRING)
  private TargetType targetType; // POST, COMMENT, USER

  private String targetId;

  private String action; // AUTO_BAN, ADMIN_BAN, ADMIN_RESTORE

  private String reason;

  // Actor ID (who performed the action) - null if automated
  @Column(name = "actor_id")
  private Long actorId;
}
