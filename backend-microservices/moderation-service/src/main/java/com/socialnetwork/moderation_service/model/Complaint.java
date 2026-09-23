package com.socialnetwork.moderation_service.model;

import com.socialnetwork.common.entity.BaseEntity;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.enums.ComplaintStatus;
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

/** A request from the owner of hidden content asking for it to be restored. */
@Entity
@Table(name = "complaints")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Complaint extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @Builder.Default
  private ComplaintStatus status = ComplaintStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TargetType targetType;

  @Column(nullable = false)
  private String targetId;

  /** Author of the complaint. */
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(columnDefinition = "TEXT")
  private String content;

  @Column(columnDefinition = "TEXT")
  private String adminResponse;
}
