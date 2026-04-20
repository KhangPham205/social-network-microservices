package com.socialnetwork.chat_service.model;

import com.socialnetwork.chat_service.enums.MessageType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "messages")
@CompoundIndex(name = "room_createdAt_idx", def = "{'roomId': 1, 'createdAt': -1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
  @Id private String id;

  @Indexed private Long roomId;

  private Long senderId;
  private String senderName;
  private String senderAvatar;

  private Long replyToId;
  private String content;

  private MessageType type;

  // Lưu list file media (url, type)
  private List<Map<String, Object>> media;

  private Instant createdAt;

  // Danh sách ID người đã đọc
  private List<Long> readBy;

  // Trạng thái xóa
  private Boolean isDeleted;
  private Instant deletedAt;
  private Boolean isSystemBan;
}
