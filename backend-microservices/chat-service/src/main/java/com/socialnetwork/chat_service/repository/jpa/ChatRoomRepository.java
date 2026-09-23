package com.socialnetwork.chat_service.repository.jpa;

import com.socialnetwork.chat_service.model.ChatRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

  /** Private rooms are unique per normalised pair key; see {@link ChatRoom#pairKeyOf}. */
  Optional<ChatRoom> findByPairKey(String pairKey);
}
