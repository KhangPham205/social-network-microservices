package com.socialnetwork.user_service.model;

import entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import vo.friendship.FriendshipStatus;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "friendship")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Friendship extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY) // Thêm LAZY
  @JoinColumn(name = "user_id", nullable = false)
  private User sender;

  @ManyToOne(fetch = FetchType.LAZY) // Thêm LAZY
  @JoinColumn(name = "friend_id", nullable = false)
  private User receiver;

  @Enumerated(EnumType.STRING)
  private FriendshipStatus status;
}
