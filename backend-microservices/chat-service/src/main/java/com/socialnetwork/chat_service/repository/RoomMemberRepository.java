package com.socialnetwork.chat_service.repository;

import com.socialnetwork.chat_service.model.RoomMember;
import com.socialnetwork.chat_service.model.RoomMemberId;
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

  // Lấy danh sách phòng chat của 1 User
  @Query("SELECT rm FROM RoomMember rm JOIN FETCH rm.chatRoom WHERE rm.id.userId = :userId")
  List<RoomMember> findRoomsByUserId(@Param("userId") Long userId);
}
