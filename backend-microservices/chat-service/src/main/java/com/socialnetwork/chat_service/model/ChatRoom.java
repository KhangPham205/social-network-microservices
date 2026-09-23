package com.socialnetwork.chat_service.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A conversation. Private rooms carry a normalised {@link #pairKey} ("min:max" of the two member
 * ids) with a unique index, which is what makes "one private room per pair" race-proof.
 */
@Entity
@Table(
    name = "chat_rooms",
    indexes = {@Index(name = "ux_chat_rooms_pair_key", columnList = "pair_key", unique = true)})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "is_group", nullable = false)
  private boolean isGroup;

  /** {@code "<minUserId>:<maxUserId>"} for private rooms, {@code null} for groups. */
  @Column(name = "pair_key", length = 64)
  private String pairKey;

  private String title;

  private String mediaUrl;

  /** Archived rooms stay readable but reject new messages and drop out of listings. */
  @Builder.Default
  @Column(nullable = false)
  private boolean active = true;

  private Instant createdAt;
  private Instant updatedAt;

  /** Never part of equality or {@code toString()}: loading it would trigger the lazy collection. */
  @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private Set<RoomMember> members;

  /** Normalised key identifying the pair of users of a private room. */
  public static String pairKeyOf(Long userA, Long userB) {
    long min = Math.min(userA, userB);
    long max = Math.max(userA, userB);
    return min + ":" + max;
  }
}
