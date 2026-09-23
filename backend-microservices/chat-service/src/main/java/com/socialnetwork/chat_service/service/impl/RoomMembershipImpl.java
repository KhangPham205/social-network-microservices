package com.socialnetwork.chat_service.service.impl;

import com.socialnetwork.chat_service.repository.jpa.RoomMemberRepository;
import com.socialnetwork.chat_service.service.RoomMembership;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomMembershipImpl implements RoomMembership {

  private final RoomMemberRepository roomMemberRepository;

  @Override
  @Transactional(readOnly = true)
  public boolean isMember(Long roomId, Long userId) {
    if (roomId == null || userId == null) {
      return false;
    }
    return roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId);
  }
}
