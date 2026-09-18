package com.socialnetwork.notification_service.model;

import com.socialnetwork.common.entity.BaseEntity;
import com.socialnetwork.common.vo.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** One notification delivered to one receiver. {@code eventId} is the idempotency key. */
@Entity
@Table(
    name = "notifications",
    indexes = {
      @Index(name = "ix_notifications_receiver_created", columnList = "receiver_id, created_at"),
      @Index(name = "ix_notifications_receiver_read", columnList = "receiver_id, is_read")
    },
    uniqueConstraints = @UniqueConstraint(name = "uk_notifications_event_id", columnNames = "event_id"))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Notification extends BaseEntity {

  /** Producer-generated id of the originating event; unique so redeliveries are ignored. */
  @Column(name = "event_id", length = 100)
  private String eventId;

  @Column(name = "receiver_id", nullable = false)
  private Long receiverId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_id", nullable = false)
  private UserCache actor;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private NotificationType type;

  private String content;

  private String link;

  @Column(name = "is_read", nullable = false)
  private boolean read;
}
