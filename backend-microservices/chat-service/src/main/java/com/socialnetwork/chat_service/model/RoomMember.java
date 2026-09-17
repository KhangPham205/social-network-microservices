package com.socialnetwork.chat_service.model;

import com.socialnetwork.chat_service.enums.ChatLabel;
import com.socialnetwork.chat_service.enums.ConversationRole;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.*;

@Entity
@Table(name = "room_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMember {

  @EmbeddedId private RoomMemberId id;

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

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "room_member_labels",
      joinColumns = {@JoinColumn(name = "room_id"), @JoinColumn(name = "user_id")})
  @Enumerated(EnumType.STRING)
  @Column(name = "label")
  private Set<ChatLabel> labels = new HashSet<>();
}
