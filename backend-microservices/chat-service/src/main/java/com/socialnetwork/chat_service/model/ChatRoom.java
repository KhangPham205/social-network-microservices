package com.socialnetwork.chat_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Set;
import lombok.*;

@Entity
@Table(name = "chat_rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Boolean isGroup;

  private String title;

  private String mediaUrl;

  private Instant createdAt;
  private Instant updatedAt;

  @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<RoomMember> members;
}
