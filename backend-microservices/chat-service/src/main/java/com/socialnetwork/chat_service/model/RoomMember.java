package com.socialnetwork.chat_service.model;

import com.socialnetwork.chat_service.enums.ConversationRole;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "room_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMember {

  @Id private RoomMemberId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("roomId")
  @JoinColumn(name = "room_id")
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private ChatRoom chatRoom;

  private Instant joinedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ConversationRole role;
}
