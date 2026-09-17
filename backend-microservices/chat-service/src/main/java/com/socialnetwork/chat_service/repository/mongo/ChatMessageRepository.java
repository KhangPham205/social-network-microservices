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
  // Lấy tin nhắn mới nhất của một phòng (dùng để hiển thị ở danh sách hội thoại)
  Optional<ChatMessage> findFirstByRoomIdOrderByCreatedAtDesc(Long roomId);

  // Xử lý Cursor Paging
  Slice<ChatMessage> findByRoomIdAndIdLessThanOrderByCreatedAtDesc(
      Long roomId, String cursorId, Pageable pageable);

  // Lấy trang đầu tiên (khi chưa có cursor)
  Slice<ChatMessage> findByRoomIdOrderByCreatedAtDesc(Long roomId, Pageable pageable);

  List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(Long roomId);

  // Đổi Asc thành Desc
  List<ChatMessage> findByRoomIdOrderByCreatedAtDesc(Long roomId);
}
