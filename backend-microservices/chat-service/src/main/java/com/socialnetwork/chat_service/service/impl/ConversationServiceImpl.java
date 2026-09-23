package com.socialnetwork.chat_service.service.impl;

import com.socialnetwork.chat_service.dto.AddMembersRequest;
import com.socialnetwork.chat_service.dto.ConversationCreateRequest;
import com.socialnetwork.chat_service.dto.ConversationResponse;
import com.socialnetwork.chat_service.dto.ConversationSummaryResponse;
import com.socialnetwork.chat_service.dto.MarkReadRequest;
import com.socialnetwork.chat_service.dto.MessageResponse;
import com.socialnetwork.chat_service.dto.ParticipantDto;
import com.socialnetwork.chat_service.dto.RoomEvent;
import com.socialnetwork.chat_service.dto.UpdateConversationRequest;
import com.socialnetwork.chat_service.dto.UpdateMemberRoleRequest;
import com.socialnetwork.chat_service.enums.ChatLabel;
import com.socialnetwork.chat_service.enums.ConversationRole;
import com.socialnetwork.chat_service.enums.MessageType;
import com.socialnetwork.chat_service.mapper.MessageMapper;
import com.socialnetwork.chat_service.model.ChatMessage;
import com.socialnetwork.chat_service.model.ChatRoom;
import com.socialnetwork.chat_service.model.RoomMember;
import com.socialnetwork.chat_service.model.RoomMemberId;
import com.socialnetwork.chat_service.repository.jpa.ChatRoomRepository;
import com.socialnetwork.chat_service.repository.jpa.RoomMemberRepository;
import com.socialnetwork.chat_service.repository.mongo.ChatMessageRepository;
import com.socialnetwork.chat_service.service.ConversationService;
import com.socialnetwork.chat_service.service.UserDirectory;
import com.socialnetwork.chat_service.support.TransactionSupport;
import com.socialnetwork.common.constants.WebSocketConstants;
import com.socialnetwork.common.dto.UserSummary;
import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ConflictException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.security.SecurityUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationServiceImpl implements ConversationService {

  private final ChatRoomRepository chatRoomRepository;
  private final RoomMemberRepository roomMemberRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatRoomWriter chatRoomWriter;
  private final UserDirectory userDirectory;
  private final SimpMessagingTemplate messagingTemplate;

  // --------------------------------------------------------------- creation

  @Override
  public ConversationResponse createConversation(ConversationCreateRequest req) {
    Long creatorId = SecurityUtils.getCurrentUserId();
    Set<Long> others = otherMembers(req.getMemberIds(), creatorId);

    if (req.isGroup()) {
      if (!StringUtils.hasText(req.getTitle())) {
        throw new BadRequestException("A group conversation requires a title");
      }
      if (others.isEmpty()) {
        throw new BadRequestException("A group conversation requires at least one other member");
      }
      ChatRoom room =
          chatRoomWriter.createGroupRoom(creatorId, req.getTitle(), req.getMediaUrl(), others);
      UserSummary creator = userDirectory.getSummary(creatorId);
      saveAndSendSystemMessage(room.getId(), creator, creator.displayName() + " đã tạo nhóm.");
      return mapToResponse(room);
    }

    if (others.size() != 1) {
      throw new BadRequestException("A private conversation must have exactly one other member");
    }
    return openPrivateRoom(creatorId, others.iterator().next());
  }

  @Override
  public ConversationResponse createConversationForFriends(Long userId1, Long userId2) {
    if (userId1 == null || userId2 == null || userId1.equals(userId2)) {
      throw new IllegalArgumentException("FriendAcceptedEvent needs two distinct user ids");
    }
    return openPrivateRoom(userId1, userId2);
  }

  @Override
  public void archiveConversationForFriends(Long userId1, Long userId2) {
    if (userId1 == null || userId2 == null || userId1.equals(userId2)) {
      throw new IllegalArgumentException("FriendshipDeletedEvent needs two distinct user ids");
    }
    Optional<ChatRoom> room =
        chatRoomRepository.findByPairKey(ChatRoom.pairKeyOf(userId1, userId2));
    if (room.isEmpty()) {
      log.debug("No private room to archive for users {} and {}", userId1, userId2);
      return;
    }

    ChatRoom archived = room.get();
    chatRoomWriter.archive(archived);
    messagingTemplate.convertAndSend(
        conversationTopic(archived.getId()), RoomEvent.conversationArchived(archived.getId()));
    log.info("Archived private conversation {} after friendship removal", archived.getId());
  }

  /** Returns the existing private room, creating it when needed - including when a race is lost. */
  private ConversationResponse openPrivateRoom(Long creatorId, Long recipientId) {
    String pairKey = ChatRoom.pairKeyOf(creatorId, recipientId);
    Optional<ChatRoom> existing = chatRoomRepository.findByPairKey(pairKey);
    if (existing.isPresent()) {
      return mapToResponse(chatRoomWriter.reactivate(existing.get()));
    }

    try {
      ChatRoom room =
          chatRoomWriter.createPrivateRoom(creatorId, recipientId, ConversationRole.MEMBER);
      log.info(
          "Created private conversation {} for users {} and {}",
          room.getId(),
          creatorId,
          recipientId);
      return mapToResponse(room);
    } catch (DataIntegrityViolationException e) {
      log.debug("Concurrent creation of private room {}, reusing the winner", pairKey);
      return chatRoomRepository
          .findByPairKey(pairKey)
          .map(this::mapToResponse)
          .orElseThrow(() -> new ConflictException("Could not open the private conversation"));
    }
  }

  // ------------------------------------------------------------ group admin

  @Override
  @Transactional
  public ConversationSummaryResponse updateConversation(
      Long currentUserId, UpdateConversationRequest request) {
    RoomMember member = requireGroupMember(request.getConversationId(), currentUserId);
    ChatRoom room = member.getChatRoom();
    requireManager(member, "Only the owner or an admin can update the group");

    boolean updated = false;
    if (StringUtils.hasText(request.getTitle())) {
      room.setTitle(request.getTitle());
      updated = true;
    }
    if (StringUtils.hasText(request.getMediaUrl())) {
      room.setMediaUrl(request.getMediaUrl());
      updated = true;
    }

    if (updated) {
      touch(room);
      UserSummary actor = userDirectory.getSummary(currentUserId);
      saveAndSendSystemMessage(
          room.getId(), actor, actor.displayName() + " đã cập nhật thông tin nhóm.");
    }
    return toSummary(room, member, currentUserId);
  }

  @Override
  @Transactional
  public ConversationSummaryResponse addMembersToGroup(
      Long currentUserId, AddMembersRequest request) {
    RoomMember actorMember = requireGroupMember(request.getConversationId(), currentUserId);
    ChatRoom room = actorMember.getChatRoom();
    requireManager(actorMember, "Only the owner or an admin can add members");

    Set<Long> existing = new HashSet<>(roomMemberRepository.findUserIdsByRoomId(room.getId()));
    List<Long> newMemberIds =
        request.getUserIds().stream().distinct().filter(id -> !existing.contains(id)).toList();
    if (newMemberIds.isEmpty()) {
      return toSummary(room, actorMember, currentUserId);
    }

    Map<Long, UserSummary> summaries =
        userDirectory.getSummaries(concat(newMemberIds, currentUserId));

    List<RoomMember> members = new ArrayList<>();
    for (Long newMemberId : newMemberIds) {
      members.add(
          RoomMember.builder()
              .id(new RoomMemberId(room.getId(), newMemberId))
              .chatRoom(room)
              .joinedAt(Instant.now())
              .role(ConversationRole.MEMBER)
              .build());
    }
    roomMemberRepository.saveAll(members);
    touch(room);

    UserSummary actor = summaries.get(currentUserId);
    String added =
        String.join(
            ", ", newMemberIds.stream().map(id -> summaries.get(id).displayName()).toList());
    saveAndSendSystemMessage(
        room.getId(), actor, actor.displayName() + " đã thêm " + added + " vào nhóm.");
    return toSummary(room, actorMember, currentUserId);
  }

  @Override
  @Transactional
  public ConversationSummaryResponse removeMemberFromGroup(
      Long currentUserId, Long conversationId, Long userIdToRemove) {
    RoomMember actorMember = requireGroupMember(conversationId, currentUserId);
    ChatRoom room = actorMember.getChatRoom();

    if (currentUserId.equals(userIdToRemove)) {
      throw new BadRequestException("Use the leave endpoint to remove yourself");
    }

    RoomMember target =
        roomMemberRepository
            .findByIdRoomIdAndIdUserId(conversationId, userIdToRemove)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found in this group"));
    validateRemovePermission(actorMember.getRole(), target.getRole());

    roomMemberRepository.delete(target);
    touch(room);

    Map<Long, UserSummary> summaries =
        userDirectory.getSummaries(List.of(currentUserId, userIdToRemove));
    UserSummary actor = summaries.get(currentUserId);
    saveAndSendSystemMessage(
        room.getId(),
        actor,
        actor.displayName()
            + " đã xóa "
            + summaries.get(userIdToRemove).displayName()
            + " khỏi nhóm.");
    return toSummary(room, actorMember, currentUserId);
  }

  @Override
  @Transactional
  public void leaveConversation(Long currentUserId, Long conversationId) {
    RoomMember member =
        roomMemberRepository
            .findByIdRoomIdAndIdUserId(conversationId, currentUserId)
            .orElseThrow(
                () -> new AccessDeniedException("You are not a member of this conversation"));
    ChatRoom room = member.getChatRoom();

    if (member.getRole() == ConversationRole.OWNER) {
      if (roomMemberRepository.countByIdRoomId(conversationId) > 1) {
        throw new BadRequestException("Transfer ownership before leaving the group");
      }
      chatRoomRepository.delete(room);
      TransactionSupport.afterCommit(
          () ->
              messagingTemplate.convertAndSend(
                  conversationTopic(conversationId),
                  RoomEvent.conversationDeleted(conversationId)));
      log.info("Conversation {} deleted by its last member {}", conversationId, currentUserId);
      return;
    }

    roomMemberRepository.delete(member);
    if (room.isGroup()) {
      touch(room);
      UserSummary actor = userDirectory.getSummary(currentUserId);
      saveAndSendSystemMessage(room.getId(), actor, actor.displayName() + " đã rời nhóm.");
    }
  }

  @Override
  @Transactional
  public ConversationSummaryResponse updateMemberRole(
      Long currentUserId, UpdateMemberRoleRequest request) {
    RoomMember actorMember = requireGroupMember(request.getConversationId(), currentUserId);
    ChatRoom room = actorMember.getChatRoom();
    requireManager(actorMember, "Only the owner or an admin can change roles");

    RoomMember target =
        roomMemberRepository
            .findByIdRoomIdAndIdUserId(request.getConversationId(), request.getUserIdToChange())
            .orElseThrow(() -> new ResourceNotFoundException("Member not found in this group"));

    if (target.getRole() == ConversationRole.OWNER) {
      throw new BadRequestException("The owner role cannot be changed");
    }
    if (request.getNewRole() == ConversationRole.OWNER) {
      throw new BadRequestException("The owner role cannot be granted through this endpoint");
    }
    if (actorMember.getRole() == ConversationRole.ADMIN
        && target.getRole() == ConversationRole.ADMIN) {
      throw new AccessDeniedException("An admin cannot change the role of another admin");
    }

    target.setRole(request.getNewRole());
    roomMemberRepository.save(target);
    touch(room);

    Map<Long, UserSummary> summaries =
        userDirectory.getSummaries(List.of(currentUserId, request.getUserIdToChange()));
    UserSummary actor = summaries.get(currentUserId);
    saveAndSendSystemMessage(
        room.getId(),
        actor,
        summaries.get(request.getUserIdToChange()).displayName()
            + " đã được đổi vai trò thành "
            + request.getNewRole().name()
            + ".");
    return toSummary(room, actorMember, currentUserId);
  }

  // ----------------------------------------------------------------- reads

  @Override
  @Transactional(readOnly = true)
  public List<ConversationSummaryResponse> getUserConversations(Long userId) {
    List<RoomMember> memberships = roomMemberRepository.findActiveRoomsByUserId(userId);
    if (memberships.isEmpty()) {
      return List.of();
    }

    List<Long> roomIds = memberships.stream().map(m -> m.getId().getRoomId()).toList();
    Map<Long, List<RoomMember>> membersByRoom = new LinkedHashMap<>();
    Set<Long> participantIds = new LinkedHashSet<>();
    for (RoomMember member : roomMemberRepository.findByIdRoomIdIn(roomIds)) {
      membersByRoom
          .computeIfAbsent(member.getId().getRoomId(), id -> new ArrayList<>())
          .add(member);
      participantIds.add(member.getId().getUserId());
    }

    // One call to user-service for every participant of every conversation.
    Map<Long, UserSummary> summaries = userDirectory.getSummaries(participantIds);

    return memberships.stream()
        .map(
            membership ->
                buildSummary(
                    membership.getChatRoom(),
                    membership,
                    userId,
                    membersByRoom.getOrDefault(membership.getId().getRoomId(), List.of()),
                    summaries))
        .sorted(
            Comparator.comparing(
                ConversationSummaryResponse::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ConversationSummaryResponse getConversationById(Long currentUserId, Long conversationId) {
    ChatRoom room =
        chatRoomRepository
            .findById(conversationId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Conversation " + conversationId + " not found"));
    RoomMember member =
        roomMemberRepository
            .findByIdRoomIdAndIdUserId(conversationId, currentUserId)
            .orElseThrow(
                () -> new AccessDeniedException("You are not a member of this conversation"));
    return toSummary(room, member, currentUserId);
  }

  // ------------------------------------------------------------- read state

  @Override
  public void markMessageAsRead(Long userId, MarkReadRequest request) {
    if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(request.getConversationId(), userId)) {
      throw new AccessDeniedException("You are not a member of this conversation");
    }

    ChatMessage message =
        chatMessageRepository
            .findById(request.getMessageId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Message " + request.getMessageId() + " not found"));

    if (!request.getConversationId().equals(message.getRoomId())) {
      throw new ResourceNotFoundException(
          "Message " + request.getMessageId() + " is not part of conversation "
              + request.getConversationId());
    }

    List<Long> readBy =
        message.getReadBy() == null ? new ArrayList<>() : new ArrayList<>(message.getReadBy());
    if (readBy.contains(userId)) {
      return;
    }

    readBy.add(userId);
    message.setReadBy(readBy);
    chatMessageRepository.save(message);

    TransactionSupport.afterCommit(
        () ->
            messagingTemplate.convertAndSend(
                conversationTopic(request.getConversationId()),
                RoomEvent.messageRead(request.getConversationId(), message.getId(), userId)));
  }

  // ---------------------------------------------------------------- labels

  @Override
  @Transactional
  public void addLabelToConversation(Long currentUserId, Long roomId, ChatLabel label) {
    RoomMember member = requireMember(roomId, currentUserId);
    member.getLabels().add(label);
    roomMemberRepository.save(member);
  }

  @Override
  @Transactional
  public void removeLabelFromConversation(Long currentUserId, Long roomId, ChatLabel label) {
    RoomMember member = requireMember(roomId, currentUserId);
    member.getLabels().remove(label);
    roomMemberRepository.save(member);
  }

  // --------------------------------------------------------------- helpers

  private void saveAndSendSystemMessage(Long roomId, UserSummary actor, String content) {
    ChatMessage saved =
        chatMessageRepository.save(
            ChatMessage.builder()
                .roomId(roomId)
                .senderId(actor.id())
                .senderName(actor.displayName())
                .senderAvatar(actor.avatarUrl())
                .content(content)
                .type(MessageType.SYSTEM)
                .createdAt(Instant.now())
                .readBy(new ArrayList<>())
                .isDeleted(false)
                .isSystemBan(false)
                .build());

    MessageResponse payload = MessageMapper.toResponse(saved);
    TransactionSupport.afterCommit(
        () -> messagingTemplate.convertAndSend(conversationQueue(roomId), payload));
  }

  private ConversationSummaryResponse toSummary(
      ChatRoom room, RoomMember currentMember, Long viewerId) {
    List<RoomMember> members = roomMemberRepository.findByIdRoomId(room.getId());
    Map<Long, UserSummary> summaries =
        userDirectory.getSummaries(members.stream().map(m -> m.getId().getUserId()).toList());
    return buildSummary(room, currentMember, viewerId, members, summaries);
  }

  private ConversationSummaryResponse buildSummary(
      ChatRoom room,
      RoomMember currentMember,
      Long viewerId,
      List<RoomMember> members,
      Map<Long, UserSummary> summaries) {

    List<ParticipantDto> participants =
        members.stream()
            .map(
                member -> {
                  UserSummary summary = summaries.get(member.getId().getUserId());
                  return ParticipantDto.builder()
                      .id(member.getId().getUserId())
                      .displayName(summary == null ? null : summary.displayName())
                      .avatarUrl(summary == null ? null : summary.avatarUrl())
                      .role(member.getRole())
                      .build();
                })
            .toList();

    String title = room.getTitle();
    String mediaUrl = room.getMediaUrl();
    if (!room.isGroup()) {
      ParticipantDto other =
          participants.stream().filter(p -> !p.getId().equals(viewerId)).findFirst().orElse(null);
      if (other != null) {
        title = other.getDisplayName();
        mediaUrl = other.getAvatarUrl();
      }
    }

    MessageResponse lastMessage =
        chatMessageRepository
            .findFirstByRoomIdOrderByCreatedAtDesc(room.getId())
            .map(MessageMapper::toResponse)
            .orElse(null);

    return ConversationSummaryResponse.builder()
        .id(room.getId())
        .title(title)
        .mediaUrl(mediaUrl)
        .isGroup(room.isGroup())
        .lastMessage(lastMessage)
        .participants(participants)
        .labels(currentMember == null ? Set.of() : currentMember.getLabels())
        .updatedAt(room.getUpdatedAt())
        .build();
  }

  private ConversationResponse mapToResponse(ChatRoom room) {
    return ConversationResponse.builder()
        .id(room.getId())
        .isGroup(room.isGroup())
        .title(room.getTitle())
        .mediaUrl(room.getMediaUrl())
        .createdAt(room.getCreatedAt())
        .memberIds(roomMemberRepository.findUserIdsByRoomId(room.getId()))
        .build();
  }

  private RoomMember requireMember(Long roomId, Long userId) {
    return roomMemberRepository
        .findByIdRoomIdAndIdUserId(roomId, userId)
        .orElseThrow(() -> new AccessDeniedException("You are not a member of this conversation"));
  }

  private RoomMember requireGroupMember(Long roomId, Long userId) {
    RoomMember member = requireMember(roomId, userId);
    if (!member.getChatRoom().isGroup()) {
      throw new BadRequestException("This action is only valid for group conversations");
    }
    return member;
  }

  private static void requireManager(RoomMember member, String message) {
    if (member.getRole() == ConversationRole.MEMBER) {
      throw new AccessDeniedException(message);
    }
  }

  private static void validateRemovePermission(ConversationRole actor, ConversationRole target) {
    if (actor == ConversationRole.MEMBER) {
      throw new AccessDeniedException("Only the owner or an admin can remove members");
    }
    if (target == ConversationRole.OWNER) {
      throw new AccessDeniedException("The owner cannot be removed");
    }
    if (actor == ConversationRole.ADMIN && target == ConversationRole.ADMIN) {
      throw new AccessDeniedException("An admin cannot remove another admin");
    }
  }

  private void touch(ChatRoom room) {
    room.setUpdatedAt(Instant.now());
    chatRoomRepository.save(room);
  }

  private static Set<Long> otherMembers(Collection<Long> memberIds, Long creatorId) {
    Set<Long> others = new LinkedHashSet<>(memberIds == null ? List.<Long>of() : memberIds);
    others.remove(null);
    others.remove(creatorId);
    return others;
  }

  private static List<Long> concat(List<Long> ids, Long extra) {
    List<Long> all = new ArrayList<>(ids);
    all.add(extra);
    return all;
  }

  private static String conversationQueue(Long roomId) {
    return WebSocketConstants.CONVERSATION_QUEUE + "/" + roomId;
  }

  private static String conversationTopic(Long roomId) {
    return WebSocketConstants.CONVERSATION_TOPIC + "/" + roomId;
  }
}
