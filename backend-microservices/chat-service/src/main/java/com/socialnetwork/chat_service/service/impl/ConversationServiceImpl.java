package com.socialnetwork.chat_service.service.impl;

import com.socialnetwork.chat_service.client.UserClient;
import com.socialnetwork.chat_service.dto.*;
import com.socialnetwork.chat_service.enums.ChatLabel;
import com.socialnetwork.chat_service.enums.ConversationRole;
import com.socialnetwork.chat_service.enums.MessageType;
import com.socialnetwork.chat_service.model.ChatMessage;
import com.socialnetwork.chat_service.model.ChatRoom;
import com.socialnetwork.chat_service.model.RoomMember;
import com.socialnetwork.chat_service.model.RoomMemberId;
import com.socialnetwork.chat_service.repository.jpa.ChatRoomRepository;
import com.socialnetwork.chat_service.repository.jpa.RoomMemberRepository;
import com.socialnetwork.chat_service.repository.mongo.ChatMessageRepository;
import com.socialnetwork.chat_service.service.ConversationService;
import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationServiceImpl implements ConversationService {

  private final ChatRoomRepository chatRoomRepository;
  private final RoomMemberRepository roomMemberRepository;
  private final ChatMessageRepository chatMessageRepository;

  private final UserClient userClient;
  private final SimpMessagingTemplate messagingTemplate;

  // Lấy ID người dùng từ Security Context
  private Long getCurrentUserId() {
    String userIdStr =
        (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return Long.parseLong(userIdStr);
  }

  @Override
  @Transactional
  public ConversationResponse createConversation(ConversationCreateRequest req) {
    Long creatorId = getCurrentUserId();
    System.out.println("Người tạo" + creatorId);
    UserProfileDto creatorProfile = userClient.getUserProfile(creatorId);

    if (Boolean.FALSE.equals(req.getIsGroup())) {
      if (req.getMemberIds() == null || req.getMemberIds().isEmpty()) {
        throw new BadRequestException("Private conversation must have a recipient.");
      }

      Long recipientId = req.getMemberIds().get(0);
      if (creatorId.equals(recipientId)) {
        throw new BadRequestException("Cannot create conversation with yourself.");
      }

      Optional<ChatRoom> existing =
          chatRoomRepository.findExistingPrivateRoom(creatorId, recipientId);
      if (existing.isPresent()) {
        return mapToResponse(existing.get());
      }
    }

    ChatRoom room =
        ChatRoom.builder()
            .isGroup(Boolean.TRUE.equals(req.getIsGroup()))
            .title(req.getTitle())
            .mediaUrl(req.getMediaUrl())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    ChatRoom savedRoom = chatRoomRepository.save(room);

    List<RoomMember> membersToSave = new ArrayList<>();
    membersToSave.add(
        RoomMember.builder()
            .id(new RoomMemberId(savedRoom.getId(), creatorId))
            .chatRoom(savedRoom)
            .joinedAt(Instant.now())
            .role(ConversationRole.OWNER)
            .build());

    if (req.getMemberIds() != null) {
      Set<Long> uniqueIds = new HashSet<>(req.getMemberIds());
      uniqueIds.remove(creatorId);

      for (Long memberId : uniqueIds) {
        membersToSave.add(
            RoomMember.builder()
                .id(new RoomMemberId(savedRoom.getId(), memberId))
                .chatRoom(savedRoom)
                .joinedAt(Instant.now())
                .role(ConversationRole.MEMBER)
                .build());
      }
    }
    roomMemberRepository.saveAll(membersToSave);

    if (Boolean.TRUE.equals(savedRoom.getIsGroup())) {
      saveAndSendSystemMessage(
          savedRoom.getId(), creatorProfile, creatorProfile.getDisplayName() + " đã tạo nhóm.");
    }

    return mapToResponse(savedRoom);
  }

  @Override
  @Transactional
  public ConversationSummaryResponse updateConversation(
      Long currentUserId, UpdateConversationRequest request) {
    RoomMember member = checkGroupAndGetMember(request.getConversationId(), currentUserId);
    ChatRoom room = member.getChatRoom();

    if (member.getRole() == ConversationRole.MEMBER) {
      throw new AccessDeniedException("Only OWNER or ADMIN can update group information.");
    }

    boolean isUpdated = false;
    if (request.getTitle() != null && !request.getTitle().isBlank()) {
      room.setTitle(request.getTitle());
      isUpdated = true;
    }

    if (request.getMediaUrl() != null && !request.getMediaUrl().isBlank()) {
      room.setMediaUrl(request.getMediaUrl());
      isUpdated = true;
    }

    if (isUpdated) {
      room.setUpdatedAt(Instant.now());
      chatRoomRepository.save(room);

      UserProfileDto profile = userClient.getUserProfile(currentUserId);
      saveAndSendSystemMessage(
          room.getId(), profile, profile.getDisplayName() + " đã cập nhật thông tin nhóm.");
    }

    return toConversationSummaryDto(room, member, currentUserId);
  }

  @Override
  @Transactional
  public ConversationSummaryResponse addMembersToGroup(
      Long currentUserId, AddMembersRequest request) {
    RoomMember currentUserMember =
        checkGroupAndGetMember(request.getConversationId(), currentUserId);
    ChatRoom room = currentUserMember.getChatRoom();

    if (currentUserMember.getRole() == ConversationRole.MEMBER) {
      throw new AccessDeniedException("Only OWNER or ADMIN can add new members");
    }

    Set<Long> existingMemberIds =
        roomMemberRepository.findByIdRoomId(room.getId()).stream()
            .map(rm -> rm.getId().getUserId())
            .collect(Collectors.toSet());

    List<Long> newMemberIds =
        request.getUserIds().stream()
            .filter(id -> !existingMemberIds.contains(id))
            .distinct()
            .toList();

    if (newMemberIds.isEmpty())
      return toConversationSummaryDto(room, currentUserMember, currentUserId);

    List<RoomMember> newMembers = new ArrayList<>();
    List<String> addedNames = new ArrayList<>();

    // Fetch user profiles & create members
    for (Long newId : newMemberIds) {
      UserProfileDto profile = userClient.getUserProfile(newId);
      addedNames.add(profile.getDisplayName());
      newMembers.add(
          RoomMember.builder()
              .id(new RoomMemberId(room.getId(), newId))
              .chatRoom(room)
              .role(ConversationRole.MEMBER)
              .joinedAt(Instant.now())
              .build());
    }
    roomMemberRepository.saveAll(newMembers);

    room.setUpdatedAt(Instant.now());
    chatRoomRepository.save(room);

    UserProfileDto currentUserProfile = userClient.getUserProfile(currentUserId);
    String addedNamesStr = String.join(", ", addedNames);
    saveAndSendSystemMessage(
        room.getId(),
        currentUserProfile,
        currentUserProfile.getDisplayName() + " đã thêm " + addedNamesStr + " vào nhóm.");

    return toConversationSummaryDto(room, currentUserMember, currentUserId);
  }

  @Override
  @Transactional
  public ConversationSummaryResponse removeMemberFromGroup(
      Long currentUserId, Long conversationId, Long userIdToRemove) {
    RoomMember currentUserMember = checkGroupAndGetMember(conversationId, currentUserId);
    ChatRoom room = currentUserMember.getChatRoom();

    if (currentUserId.equals(userIdToRemove)) {
      throw new BadRequestException("Use leaveConversation to remove yourself.");
    }

    RoomMember targetMember =
        roomMemberRepository
            .findByIdRoomIdAndIdUserId(conversationId, userIdToRemove)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found in group."));

    validateRemovePermission(currentUserMember.getRole(), targetMember.getRole());

    // Lấy tên người bị xóa để thông báo
    UserProfileDto targetProfile = userClient.getUserProfile(userIdToRemove);

    roomMemberRepository.delete(targetMember);

    room.setUpdatedAt(Instant.now());
    chatRoomRepository.save(room);

    UserProfileDto currentUserProfile = userClient.getUserProfile(currentUserId);
    saveAndSendSystemMessage(
        room.getId(),
        currentUserProfile,
        currentUserProfile.getDisplayName()
            + " đã xóa "
            + targetProfile.getDisplayName()
            + " khỏi nhóm.");

    return toConversationSummaryDto(room, currentUserMember, currentUserId);
  }

  @Override
  @Transactional
  public void leaveConversation(Long currentUserId, Long conversationId) {
    RoomMember member =
        roomMemberRepository
            .findByIdRoomIdAndIdUserId(conversationId, currentUserId)
            .orElseThrow(
                () -> new AccessDeniedException("Not a member of conversation " + conversationId));

    ChatRoom room = member.getChatRoom();

    if (member.getRole() == ConversationRole.OWNER) {
      long memberCount = roomMemberRepository.countByIdRoomId(conversationId);
      if (memberCount > 1) {
        throw new BadRequestException("Owner must transfer ownership before leaving.");
      } else {
        // Xóa phòng bên Postgres
        chatRoomRepository.delete(room);

        Map<String, Object> payload =
            Map.of("type", "EVENT_CONVERSATION_DELETED", "conversationId", conversationId);
        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId, (Object) payload);
        return;
      }
    }

    roomMemberRepository.delete(member);

    if (Boolean.TRUE.equals(room.getIsGroup())) {
      room.setUpdatedAt(Instant.now());
      chatRoomRepository.save(room);

      UserProfileDto profile = userClient.getUserProfile(currentUserId);
      saveAndSendSystemMessage(room.getId(), profile, profile.getDisplayName() + " đã rời nhóm.");
    }
  }

  @Override
  @Transactional
  public ConversationSummaryResponse updateMemberRole(
      Long currentUserId, UpdateMemberRoleRequest request) {
    RoomMember currentUserMember =
        checkGroupAndGetMember(request.getConversationId(), currentUserId);
    ChatRoom room = currentUserMember.getChatRoom();

    if (currentUserMember.getRole() == ConversationRole.MEMBER) {
      throw new AccessDeniedException("Permission denied.");
    }

    RoomMember targetMember =
        roomMemberRepository
            .findByIdRoomIdAndIdUserId(request.getConversationId(), request.getUserIdToChange())
            .orElseThrow(() -> new ResourceNotFoundException("Member not found."));

    if (targetMember.getRole() == ConversationRole.OWNER) {
      throw new BadRequestException("Cannot change OWNER role.");
    }

    if (request.getNewRole() == ConversationRole.OWNER) {
      throw new BadRequestException("Cannot assign OWNER via this API.");
    }

    targetMember.setRole(request.getNewRole());
    roomMemberRepository.save(targetMember);

    room.setUpdatedAt(Instant.now());
    chatRoomRepository.save(room);

    UserProfileDto currentProfile = userClient.getUserProfile(currentUserId);
    UserProfileDto targetProfile = userClient.getUserProfile(request.getUserIdToChange());

    saveAndSendSystemMessage(
        room.getId(),
        currentProfile,
        targetProfile.getDisplayName()
            + " đã được thăng cấp thành "
            + request.getNewRole().name()
            + ".");

    return toConversationSummaryDto(room, currentUserMember, currentUserId);
  }

  @Override
  public void markMessageAsRead(Long userId, MarkReadRequest request) {
    if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(request.getConversationId(), userId)) {
      throw new AccessDeniedException("Not a member.");
    }

    ChatMessage message =
        chatMessageRepository
            .findById(request.getMessageId())
            .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

    List<Long> readBy = message.getReadBy();
    if (readBy == null) {
      readBy = new ArrayList<>();
    }

    if (!readBy.contains(userId)) {
      readBy.add(userId);
      message.setReadBy(readBy);
      chatMessageRepository.save(message);

      Map<String, Object> payload =
          Map.of(
              "type", "EVENT_READ",
              "conversationId", request.getConversationId(),
              "messageId", message.getId(),
              "readerId", userId,
              "timestamp", Instant.now().toString());
      messagingTemplate.convertAndSend(
          "/topic/conversation/" + request.getConversationId(), (Object) payload);
    }
  }

  @Override
  @Transactional
  public void addLabelToConversation(Long roomId, ChatLabel label) {
    Long currentUserId = getCurrentUserId();

    RoomMember member =
        roomMemberRepository
            .findByIdRoomIdAndIdUserId(roomId, currentUserId)
            .orElseThrow(
                () -> new AccessDeniedException("You are not a member of this conversation"));

    member.getLabels().add(label);
    roomMemberRepository.save(member);
  }

  @Override
  @Transactional
  public void removeLabelFromConversation(Long roomId, ChatLabel label) {
    Long currentUserId = getCurrentUserId();

    RoomMember member =
        roomMemberRepository
            .findByIdRoomIdAndIdUserId(roomId, currentUserId)
            .orElseThrow(
                () -> new AccessDeniedException("You are not a member of this conversation"));

    member.getLabels().remove(label);
    roomMemberRepository.save(member);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConversationSummaryResponse> getUserConversations(Long userId) {
    List<RoomMember> members = roomMemberRepository.findRoomsByUserId(userId);

    return members.stream()
        .map(member -> toConversationSummaryDto(member.getChatRoom(), member, userId))
        .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public ConversationSummaryResponse getConversationById(Long currentUserId, Long conversationId) {
    ChatRoom room =
        chatRoomRepository
            .findById(conversationId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

    RoomMember currentMember =
        roomMemberRepository
            .findByIdRoomIdAndIdUserId(conversationId, currentUserId)
            .orElseThrow(() -> new AccessDeniedException("Not a member."));

    return toConversationSummaryDto(room, currentMember, currentUserId);
  }

  // ------------------------- HELPER METHODS -------------------------

  private void saveAndSendSystemMessage(Long roomId, UserProfileDto senderProfile, String content) {
    ChatMessage sysMsg =
        ChatMessage.builder()
            .roomId(roomId)
            .senderId(senderProfile.getId())
            .senderName(senderProfile.getDisplayName())
            .senderAvatar(senderProfile.getAvatarUrl())
            .content(content)
            .type(MessageType.SYSTEM)
            .createdAt(Instant.now())
            .isDeleted(false)
            .build();

    chatMessageRepository.save(sysMsg);

    chatRoomRepository
        .findById(roomId)
        .ifPresent(
            r -> {
              r.setUpdatedAt(Instant.now());
              chatRoomRepository.save(r);
            });

    messagingTemplate.convertAndSend("/topic/conversation/" + roomId, sysMsg);
  }

  private ConversationSummaryResponse toConversationSummaryDto(
      ChatRoom room, RoomMember currentMember, Long viewerId) {
    ChatMessage lastMessage =
        chatMessageRepository.findFirstByRoomIdOrderByCreatedAtDesc(room.getId()).orElse(null);
    Map<String, Object> lastMsgMap =
        (lastMessage != null) ? convertMessageToMap(lastMessage) : null;

    List<RoomMember> members = roomMemberRepository.findByIdRoomId(room.getId());
    List<ParticipantDto> participants = new ArrayList<>();

    for (RoomMember m : members) {
      UserProfileDto profile = userClient.getUserProfile(m.getId().getUserId());
      participants.add(
          ParticipantDto.builder()
              .id(m.getId().getUserId())
              .displayName(profile.getDisplayName())
              .avatarUrl(profile.getAvatarUrl())
              .role(m.getRole().name())
              .build());
    }

    String finalTitle = room.getTitle();
    String finalMediaUrl = room.getMediaUrl();

    if (Boolean.FALSE.equals(room.getIsGroup())) {
      ParticipantDto otherUser =
          participants.stream().filter(p -> !p.getId().equals(viewerId)).findFirst().orElse(null);

      if (otherUser != null) {
        finalTitle = otherUser.getDisplayName();
        finalMediaUrl = otherUser.getAvatarUrl();
      }
    }

    return ConversationSummaryResponse.builder()
        .id(room.getId())
        .title(finalTitle)
        .mediaUrl(finalMediaUrl)
        .isGroup(room.getIsGroup())
        .lastMessage(lastMsgMap)
        .participants(participants)
        .labels(currentMember != null ? currentMember.getLabels() : Collections.emptySet())
        .updatedAt(room.getUpdatedAt())
        .build();
  }

  private RoomMember checkGroupAndGetMember(Long roomId, Long userId) {
    RoomMember member =
        roomMemberRepository
            .findByIdRoomIdAndIdUserId(roomId, userId)
            .orElseThrow(() -> new AccessDeniedException("Not a member"));
    if (Boolean.FALSE.equals(member.getChatRoom().getIsGroup())) {
      throw new BadRequestException("Action only valid for groups.");
    }
    return member;
  }

  private void validateRemovePermission(ConversationRole actor, ConversationRole target) {
    if (actor == ConversationRole.MEMBER) throw new AccessDeniedException("Permission denied.");
    if (target == ConversationRole.OWNER) throw new AccessDeniedException("Cannot remove Owner.");
    if (actor == ConversationRole.ADMIN && target == ConversationRole.ADMIN)
      throw new AccessDeniedException("Admin cannot remove another Admin.");
  }

  private ConversationResponse mapToResponse(ChatRoom room) {
    List<Long> memberIds =
        roomMemberRepository.findByIdRoomId(room.getId()).stream()
            .map(rm -> rm.getId().getUserId())
            .toList();
    return ConversationResponse.builder()
        .id(room.getId())
        .isGroup(room.getIsGroup())
        .title(room.getTitle())
        .mediaUrl(room.getMediaUrl())
        .createdAt(room.getCreatedAt())
        .memberIds(memberIds)
        .build();
  }

  private Map<String, Object> convertMessageToMap(ChatMessage msg) {
    if (Boolean.TRUE.equals(msg.getIsDeleted()) || Boolean.TRUE.equals(msg.getIsSystemBan())) {
      return Map.of(
          "id",
          msg.getId(),
          "content",
          "Tin nhắn đã bị gỡ bỏ",
          "type",
          "REVOKED",
          "createdAt",
          msg.getCreatedAt().toString());
    }
    return Map.of(
        "id", msg.getId(),
        "content", msg.getContent() != null ? msg.getContent() : "",
        "type", msg.getType() != null ? msg.getType().name() : MessageType.TEXT.name(),
        "senderName", msg.getSenderName() != null ? msg.getSenderName() : "",
        "createdAt", msg.getCreatedAt().toString());
  }

  @Override
  @Transactional
  public ConversationResponse createConversationForFriends(Long userId1, Long userId2) {
    // Kiểm tra xem conversation đã tồn tại chưa
    Optional<ChatRoom> existing = chatRoomRepository.findExistingPrivateRoom(userId1, userId2);
    if (existing.isPresent()) {
      log.info("Conversation already exists between users {} and {}", userId1, userId2);
      return mapToResponse(existing.get());
    }

    // Tạo conversation mới
    ChatRoom room =
        ChatRoom.builder().isGroup(false).createdAt(Instant.now()).updatedAt(Instant.now()).build();

    ChatRoom savedRoom = chatRoomRepository.save(room);

    // Thêm cả 2 người vào conversation
    List<RoomMember> membersToSave = new ArrayList<>();
    membersToSave.add(
        RoomMember.builder()
            .id(new RoomMemberId(savedRoom.getId(), userId1))
            .chatRoom(savedRoom)
            .joinedAt(Instant.now())
            .role(ConversationRole.MEMBER)
            .build());

    membersToSave.add(
        RoomMember.builder()
            .id(new RoomMemberId(savedRoom.getId(), userId2))
            .chatRoom(savedRoom)
            .joinedAt(Instant.now())
            .role(ConversationRole.MEMBER)
            .build());

    roomMemberRepository.saveAll(membersToSave);

    log.info("Successfully created private conversation between users {} and {}", userId1, userId2);
    return mapToResponse(savedRoom);
  }
}
