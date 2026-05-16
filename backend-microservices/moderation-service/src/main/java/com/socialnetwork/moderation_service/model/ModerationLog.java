package com.socialnetwork.moderation_service.model;

import entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import vo.TargetType;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "moderation_logs")
@Data
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