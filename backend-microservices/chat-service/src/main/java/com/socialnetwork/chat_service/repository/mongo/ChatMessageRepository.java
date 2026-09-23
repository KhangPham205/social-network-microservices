package com.socialnetwork.chat_service.repository.mongo;

import com.socialnetwork.chat_service.model.ChatMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

  /** Last message of a room, for the conversation list preview. */
  Optional<ChatMessage> findFirstByRoomIdOrderByCreatedAtDesc(Long roomId);

  /** Cursor page: everything older than {@code cursorId}. */
  Slice<ChatMessage> findByRoomIdAndIdLessThanOrderByCreatedAtDesc(
      Long roomId, String cursorId, Pageable pageable);

  /** Newest page, when the client has no cursor yet. */
  Slice<ChatMessage> findByRoomIdOrderByCreatedAtDesc(Long roomId, Pageable pageable);

  List<ChatMessage> findByIdIn(List<String> ids);
}
