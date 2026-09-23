package com.socialnetwork.chat_service.repository.jpa;

import com.socialnetwork.chat_service.model.RoomMember;
import com.socialnetwork.chat_service.model.RoomMemberId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberId> {

  Optional<RoomMember> findByIdRoomIdAndIdUserId(Long roomId, Long userId);

  long countByIdRoomId(Long roomId);

  boolean existsByIdRoomIdAndIdUserId(Long roomId, Long userId);

  List<RoomMember> findByIdRoomId(Long roomId);

  /** Members of several rooms in one query, for the conversation listing. */
  List<RoomMember> findByIdRoomIdIn(Collection<Long> roomIds);

  @Query("SELECT rm.id.userId FROM RoomMember rm WHERE rm.id.roomId = :roomId")
  List<Long> findUserIdsByRoomId(@Param("roomId") Long roomId);

  /** Active rooms the user belongs to; archived conversations stay out of the listing. */
  @Query(
      "SELECT rm FROM RoomMember rm JOIN FETCH rm.chatRoom r "
          + "WHERE rm.id.userId = :userId AND r.active = true")
  List<RoomMember> findActiveRoomsByUserId(@Param("userId") Long userId);
}
