package com.socialnetwork.chat_service.model;

import com.socialnetwork.chat_service.enums.ChatLabel;
import com.socialnetwork.chat_service.enums.ConversationRole;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "room_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMember {

  @EmbeddedId private RoomMemberId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("roomId")
  @JoinColumn(name = "room_id")
  @ToString.Exclude
  private ChatRoom chatRoom;

  private Instant joinedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ConversationRole role;

  @Builder.Default
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "room_member_labels",
      joinColumns = {@JoinColumn(name = "room_id"), @JoinColumn(name = "user_id")})
  @Enumerated(EnumType.STRING)
  @Column(name = "label")
  private Set<ChatLabel> labels = new HashSet<>();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RoomMember other)) {
      return false;
    }
    return id != null && Objects.equals(id, other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
