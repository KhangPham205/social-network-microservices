package com.socialnetwork.chat_service.model;

import com.socialnetwork.chat_service.dto.MediaItem;
import com.socialnetwork.chat_service.enums.MessageType;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "messages")
@CompoundIndex(name = "room_createdAt_idx", def = "{'roomId': 1, 'createdAt': -1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

  @Id private String id;

  @Indexed private Long roomId;

  private Long senderId;
  private String senderName;
  private String senderAvatar;

  /** Mongo id of the message this one replies to. */
  private String replyToId;

  private String content;

  private MessageType type;

  private List<MediaItem> media;

  private Instant createdAt;

  /** Ids of the users that have read this message. */
  private List<Long> readBy;

  private Boolean isDeleted;
  private Instant deletedAt;

  /** Set by moderation-service through {@code MODERATION_ACTIONS}. */
  private Boolean isSystemBan;

  public boolean isHidden() {
    return Boolean.TRUE.equals(isDeleted) || Boolean.TRUE.equals(isSystemBan);
  }
}
