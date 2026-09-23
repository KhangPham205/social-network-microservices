package com.socialnetwork.chat_service.service.impl;

import com.socialnetwork.chat_service.enums.ConversationRole;
import com.socialnetwork.chat_service.model.ChatRoom;
import com.socialnetwork.chat_service.model.RoomMember;
import com.socialnetwork.chat_service.model.RoomMemberId;
import com.socialnetwork.chat_service.repository.jpa.ChatRoomRepository;
import com.socialnetwork.chat_service.repository.jpa.RoomMemberRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the transactions that insert rooms. It is a separate bean on purpose: the caller stays
 * outside the transaction so it can recover from the {@code pair_key} unique-index violation that a
 * concurrent creation of the same private room produces.
 */
@Service
@RequiredArgsConstructor
public class ChatRoomWriter {

  private final ChatRoomRepository chatRoomRepository;
  private final RoomMemberRepository roomMemberRepository;

  /** @throws org.springframework.dao.DataIntegrityViolationException if the pair already exists */
  @Transactional
  public ChatRoom createPrivateRoom(Long creatorId, Long recipientId, ConversationRole role) {
    ChatRoom room =
        chatRoomRepository.saveAndFlush(
            ChatRoom.builder()
                .isGroup(false)
                .pairKey(ChatRoom.pairKeyOf(creatorId, recipientId))
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    addMembers(room, List.of(creatorId, recipientId), role);
    return room;
  }

  @Transactional
  public ChatRoom createGroupRoom(
      Long creatorId, String title, String mediaUrl, Collection<Long> memberIds) {
    ChatRoom room =
        chatRoomRepository.saveAndFlush(
            ChatRoom.builder()
                .isGroup(true)
                .title(title)
                .mediaUrl(mediaUrl)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

    List<RoomMember> members = new ArrayList<>();
    members.add(newMember(room, creatorId, ConversationRole.OWNER));
    for (Long memberId : memberIds) {
      members.add(newMember(room, memberId, ConversationRole.MEMBER));
    }
    roomMemberRepository.saveAll(members);
    return room;
  }

  /** Brings an archived private room back, e.g. when the two users become friends again. */
  @Transactional
  public ChatRoom reactivate(ChatRoom room) {
    if (room.isActive()) {
      return room;
    }
    room.setActive(true);
    room.setUpdatedAt(Instant.now());
    return chatRoomRepository.save(room);
  }

  @Transactional
  public void archive(ChatRoom room) {
    if (!room.isActive()) {
      return;
    }
    room.setActive(false);
    room.setUpdatedAt(Instant.now());
    chatRoomRepository.save(room);
  }

  private void addMembers(ChatRoom room, Collection<Long> userIds, ConversationRole role) {
    List<RoomMember> members = new ArrayList<>();
    for (Long userId : userIds) {
      members.add(newMember(room, userId, role));
    }
    roomMemberRepository.saveAll(members);
  }

  private RoomMember newMember(ChatRoom room, Long userId, ConversationRole role) {
    return RoomMember.builder()
        .id(new RoomMemberId(room.getId(), userId))
        .chatRoom(room)
        .joinedAt(Instant.now())
        .role(role)
        .build();
  }
}
