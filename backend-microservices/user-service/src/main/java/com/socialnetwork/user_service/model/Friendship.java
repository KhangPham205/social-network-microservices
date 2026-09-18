package com.socialnetwork.user_service.model;

import com.socialnetwork.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import com.socialnetwork.common.vo.FriendshipStatus;

@Entity
@Table(name = "friendship")
@Getter
@Setter
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
