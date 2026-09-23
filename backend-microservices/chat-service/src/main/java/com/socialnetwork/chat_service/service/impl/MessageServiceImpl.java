package com.socialnetwork.chat_service.service.impl;

import com.socialnetwork.chat_service.dto.MessageRequest;
import com.socialnetwork.chat_service.dto.MessageResponse;
import com.socialnetwork.chat_service.dto.RoomEvent;
import com.socialnetwork.chat_service.enums.MessageType;
import com.socialnetwork.chat_service.mapper.MessageMapper;
import com.socialnetwork.chat_service.model.ChatMessage;
import com.socialnetwork.chat_service.model.ChatRoom;
import com.socialnetwork.chat_service.repository.jpa.ChatRoomRepository;
import com.socialnetwork.chat_service.repository.jpa.RoomMemberRepository;
import com.socialnetwork.chat_service.repository.mongo.ChatMessageRepository;
import com.socialnetwork.chat_service.service.MessageService;
import com.socialnetwork.chat_service.service.UserDirectory;
import com.socialnetwork.chat_service.support.TransactionSupport;
import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.constants.WebSocketConstants;
import com.socialnetwork.common.dto.MessageModerationView;
import com.socialnetwork.common.dto.UserSummary;
import com.socialnetwork.common.events.MessageCreatedEvent;
import com.socialnetwork.common.events.MessageNotificationEvent;
import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.security.SecurityUtils;
import com.socialnetwork.common.vo.CursorPage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl implements MessageService {

  /** Preview shown by notification-service when the message carries only attachments. */
  private static final String ATTACHMENT_PREVIEW = "Đã gửi 1 tệp đính kèm";

  private static final int PREVIEW_MAX_LENGTH = 120;

  private final ChatMessageRepository chatMessageRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final RoomMemberRepository roomMemberRepository;
  private final SimpMessagingTemplate messagingTemplate;
  private final UserDirectory userDirectory;
  private final KafkaTemplate<String, Object> kafkaTemplate;

  @Override
  public MessageResponse sendMessage(MessageRequest req) {
    return createAndSaveMessage(SecurityUtils.getCurrentUserId(), req);
  }

  @Override
  public MessageResponse sendMessageAs(Long senderId, MessageRequest req) {
    return createAndSaveMessage(senderId, req);
  }

  private MessageResponse createAndSaveMessage(Long senderId, MessageRequest req) {
    boolean hasContent = StringUtils.hasText(req.getContent());
    boolean hasMedia = req.getMedia() != null && !req.getMedia().isEmpty();
    if (!hasContent && !hasMedia) {
      throw new BadRequestException("A message must carry text or at least one attachment");
    }

    ChatRoom room = requireRoom(req.getConversationId());
    if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), senderId)) {
      throw new AccessDeniedException("You are not a member of this conversation");
    }
    if (!room.isActive()) {
      throw new BadRequestException("This conversation is archived and no longer accepts messages");
    }
    requireReplyBelongsToRoom(req.getReplyToId(), room.getId());

    UserSummary sender = userDirectory.getSummary(senderId);
    ChatMessage saved =
        chatMessageRepository.save(
            ChatMessage.builder()
                .roomId(room.getId())
                .senderId(senderId)
                .senderName(sender.displayName())
                .senderAvatar(sender.avatarUrl())
                .replyToId(StringUtils.hasText(req.getReplyToId()) ? req.getReplyToId() : null)
                .content(hasContent ? req.getContent() : null)
                .type(resolveType(hasContent))
                .media(hasMedia ? List.copyOf(req.getMedia()) : null)
                .createdAt(Instant.now())
                .readBy(new ArrayList<>(List.of(senderId)))
                .isDeleted(false)
                .isSystemBan(false)
                .build());

    MessageResponse response = MessageMapper.toResponse(saved);
    List<Long> recipientIds = otherMemberIds(room.getId(), senderId);
    String preview = hasContent ? truncate(req.getContent()) : ATTACHMENT_PREVIEW;

    TransactionSupport.afterCommit(
        () -> {
          messagingTemplate.convertAndSend(conversationQueue(room.getId()), response);
          kafkaTemplate.send(
              KafkaTopics.MESSAGE_CREATED,
              saved.getId(),
              new MessageCreatedEvent(saved.getId(), room.getId(), senderId, saved.getContent()));
          if (!recipientIds.isEmpty()) {
            kafkaTemplate.send(
                KafkaTopics.CHAT_NOTIFICATION,
                saved.getId(),
                new MessageNotificationEvent(
                    saved.getId(),
                    room.getId(),
                    senderId,
                    sender.displayName(),
                    preview,
                    recipientIds));
          }
        });

    log.info("User {} sent message {} in conversation {}", senderId, saved.getId(), room.getId());
    return response;
  }

  @Override
  public CursorPage<MessageResponse> getMessagesCursor(
      Long conversationId, String beforeMessageId, int limit) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(conversationId, currentUserId)) {
      throw new AccessDeniedException("You are not a member of this conversation");
    }

    PageRequest pageRequest = PageRequest.of(0, limit);
    Slice<ChatMessage> slice =
        StringUtils.hasText(beforeMessageId)
            ? chatMessageRepository.findByRoomIdAndIdLessThanOrderByCreatedAtDesc(
                conversationId, beforeMessageId, pageRequest)
            : chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(conversationId, pageRequest);

    List<ChatMessage> page = slice.getContent();
    String nextCursor =
        slice.hasNext() && !page.isEmpty() ? page.get(page.size() - 1).getId() : null;

    List<MessageResponse> content =
        new ArrayList<>(page.stream().map(MessageMapper::toResponse).toList());
    Collections.reverse(content);
    return new CursorPage<>(content, nextCursor);
  }

  @Override
  public void softDeleteMessage(String messageId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    ChatMessage message = requireMessage(messageId);

    if (!currentUserId.equals(message.getSenderId())) {
      throw new AccessDeniedException("You can only delete your own messages");
    }

    message.setIsDeleted(true);
    message.setDeletedAt(Instant.now());
    message.setContent(null);
    message.setMedia(null);
    chatMessageRepository.save(message);

    Long roomId = message.getRoomId();
    TransactionSupport.afterCommit(
        () ->
            messagingTemplate.convertAndSend(
                conversationTopic(roomId), RoomEvent.messageRevoked(roomId, messageId)));
    log.info("User {} deleted message {}", currentUserId, messageId);
  }

  @Override
  public void applySystemBan(String messageId, boolean banned) {
    ChatMessage message = requireMessage(messageId);
    if (Boolean.valueOf(banned).equals(message.getIsSystemBan())) {
      log.debug("Message {} already has systemBan={}, nothing to do", messageId, banned);
      return;
    }

    message.setIsSystemBan(banned);
    chatMessageRepository.save(message);

    Long roomId = message.getRoomId();
    TransactionSupport.afterCommit(
        () ->
            messagingTemplate.convertAndSend(
                conversationTopic(roomId), RoomEvent.messageRevoked(roomId, messageId)));
    log.info("Moderation set systemBan={} on message {}", banned, messageId);
  }

  @Override
  public MessageModerationView getModerationView(String messageId) {
    return MessageMapper.toModerationView(requireMessage(messageId));
  }

  @Override
  public Long getMessageOwnerId(String messageId) {
    return requireMessage(messageId).getSenderId();
  }

  @Override
  public List<MessageModerationView> getModerationViews(List<String> messageIds) {
    if (messageIds == null || messageIds.isEmpty()) {
      return List.of();
    }
    return chatMessageRepository.findByIdIn(messageIds).stream()
        .map(MessageMapper::toModerationView)
        .toList();
  }

  // ------------------------------------------------------------------ helpers

  private ChatRoom requireRoom(Long conversationId) {
    return chatRoomRepository
        .findById(conversationId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Conversation " + conversationId + " not found"));
  }

  private ChatMessage requireMessage(String messageId) {
    return chatMessageRepository
        .findById(messageId)
        .orElseThrow(() -> new ResourceNotFoundException("Message " + messageId + " not found"));
  }

  private void requireReplyBelongsToRoom(String replyToId, Long roomId) {
    if (!StringUtils.hasText(replyToId)) {
      return;
    }
    ChatMessage parent = requireMessage(replyToId);
    if (!roomId.equals(parent.getRoomId())) {
      throw new BadRequestException("The replied message belongs to another conversation");
    }
  }

  private List<Long> otherMemberIds(Long roomId, Long senderId) {
    return roomMemberRepository.findUserIdsByRoomId(roomId).stream()
        .filter(id -> !id.equals(senderId))
        .toList();
  }

  private static MessageType resolveType(boolean hasContent) {
    return hasContent ? MessageType.TEXT : MessageType.FILE;
  }

  private static String truncate(String content) {
    return content.length() <= PREVIEW_MAX_LENGTH
        ? content
        : content.substring(0, PREVIEW_MAX_LENGTH) + "…";
  }

  private static String conversationQueue(Long roomId) {
    return WebSocketConstants.CONVERSATION_QUEUE + "/" + roomId;
  }

  private static String conversationTopic(Long roomId) {
    return WebSocketConstants.CONVERSATION_TOPIC + "/" + roomId;
  }
}
