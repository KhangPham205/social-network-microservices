package com.socialnetwork.user_service.model;

import com.socialnetwork.common.entity.BaseEntity;
import com.socialnetwork.common.vo.FriendshipStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Directional row between two users. {@code PENDING} and {@code FRIEND} describe the pair as a
 * whole, {@code BLOCKED} only describes what {@code sender} did to {@code receiver}: both sides can
 * hold their own BLOCKED row. At most one row per ordered pair.
 */
@Entity
@Table(
    name = "friendship",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_friendship_user_friend",
            columnNames = {"user_id", "friend_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Friendship extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User sender;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "friend_id", nullable = false)
  private User receiver;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FriendshipStatus status;
}
