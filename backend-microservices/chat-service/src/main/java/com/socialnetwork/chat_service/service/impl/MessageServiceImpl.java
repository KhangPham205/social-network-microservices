package com.socialnetwork.chat_service.service.impl;

import com.socialnetwork.chat_service.client.UserClient;
import com.socialnetwork.chat_service.dto.MessageRequest;
import com.socialnetwork.chat_service.dto.MessageResponse;
import com.socialnetwork.chat_service.dto.UserProfileDto;
import com.socialnetwork.chat_service.event.MessageNotificationEvent;
import com.socialnetwork.chat_service.model.ChatMessage;
import com.socialnetwork.chat_service.model.ChatRoom;
import com.socialnetwork.chat_service.repository.ChatMessageRepository;
import com.socialnetwork.chat_service.repository.ChatRoomRepository;
import com.socialnetwork.chat_service.repository.RoomMemberRepository;
import com.socialnetwork.chat_service.service.MessageService;
import exception.AccessDeniedException;
import exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import vo.CursorPage;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl implements MessageService {

  private final ChatMessageRepository chatMessageRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final RoomMemberRepository roomMemberRepository;

  private final SimpMessagingTemplate messagingTemplate;
  private final UserClient userClient;
  private final KafkaTemplate<String, Object> kafkaTemplate;

  private Long getCurrentUserId() {
    String userIdStr =
        (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return Long.parseLong(userIdStr);
  }

  @Override
  public Map<String, Object> sendMessage(MessageRequest req) {
    Long senderId = getCurrentUserId();
    return createAndSaveMessage(senderId, req);
  }

  @Override
  public void sendMessageAs(Long senderId, MessageRequest req) {
    createAndSaveMessage(senderId, req);
  }

  private Map<String, Object> createAndSaveMessage(Long senderId, MessageRequest req) {
    UserProfileDto senderProfile = userClient.getUserProfile(senderId);

    ChatRoom room =
        chatRoomRepository
            .findById(req.getConversationId())
            .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

    ChatMessage message =
        ChatMessage.builder()
            .roomId(room.getId())
            .senderId(senderId)
            .senderName(senderProfile.getDisplayName())
            .senderAvatar(senderProfile.getAvatarUrl())
            .replyToId(req.getReplyToId())
            .content(req.getContent())
            .media(req.getMediaAttachments() != null ? req.getMediaAttachments() : List.of())
            .createdAt(Instant.now())
            .readBy(List.of(senderId))
            .isDeleted(false)
            .build();

    ChatMessage savedMessage = chatMessageRepository.save(message);

    // Update room timestamp
    room.setUpdatedAt(Instant.now());
    chatRoomRepository.save(room);

    Map<String, Object> payload = convertToMapPayload(savedMessage);
    messagingTemplate.convertAndSend("/topic/conversation/" + room.getId(), (Object) payload);

    publishNotificationEvent(room, senderProfile, req);

    return payload;
  }

  @Override
  public CursorPage<MessageResponse> getMessagesCursor(
      Long conversationId, String beforeMessageId, int limit) {
    Long currentUserId = getCurrentUserId();
    if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(conversationId, currentUserId)) {
      throw new AccessDeniedException("Not a member of this room");
    }

    PageRequest pageRequest = PageRequest.of(0, limit);
    Slice<ChatMessage> slice;

    if (beforeMessageId != null && !beforeMessageId.isBlank()) {
      slice =
          chatMessageRepository.findByRoomIdAndIdLessThanOrderByCreatedAtDesc(
              conversationId, beforeMessageId, pageRequest);
    } else {
      slice = chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(conversationId, pageRequest);
    }

    List<MessageResponse> content =
        slice.getContent().stream().map(this::mapToDto).collect(Collectors.toList());

    String nextCursor =
        slice.hasNext() ? slice.getContent().get(slice.getContent().size() - 1).getId() : null;

    return new CursorPage<>(content, nextCursor);
  }

  @Override
  public List<Map<String, Object>> getMessages(Long conversationId) {
    // Chỉ dùng cho internal/admin, cẩn thận với lượng dữ liệu lớn
    return chatMessageRepository.findAll().stream()
        .filter(m -> m.getRoomId().equals(conversationId))
        .map(this::convertToMapPayload)
        .collect(Collectors.toList());
  }

  @Override
  public void softDeleteMessage(String messageId) {
    Long currentUserId = getCurrentUserId();
    ChatMessage message =
        chatMessageRepository
            .findById(messageId)
            .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

    if (!message.getSenderId().equals(currentUserId)) {
      throw new AccessDeniedException("You can only delete your own messages");
    }

    message.setIsDeleted(true);
    message.setDeletedAt(Instant.now());
    message.setContent("Tin nhắn đã bị gỡ bỏ");
    message.setMedia(null);

    chatMessageRepository.save(message);

    // Bắn sự kiện qua Socket để Client thu hồi tin nhắn
    Map<String, Object> payload =
        Map.of(
            "type", "MESSAGE_REVOKED",
            "id", message.getId(),
            "conversationId", message.getRoomId());
    messagingTemplate.convertAndSend(
        "/topic/conversation/" + message.getRoomId(), (Object) payload);
  }

  private void publishNotificationEvent(ChatRoom room, UserProfileDto sender, MessageRequest req) {
    List<Long> recipientIds =
        roomMemberRepository.findByIdRoomId(room.getId()).stream()
            .map(rm -> rm.getId().getUserId())
            .filter(id -> !id.equals(sender.getId()))
            .collect(Collectors.toList());

    if (recipientIds.isEmpty()) return;

    String preview =
        (req.getContent() != null && !req.getContent().isEmpty())
            ? req.getContent()
            : "Đã gửi 1 tệp đính kèm";

    MessageNotificationEvent event =
        MessageNotificationEvent.builder()
            .roomId(room.getId())
            .senderId(sender.getId())
            .senderName(sender.getDisplayName())
            .previewContent(preview)
            .recipientIds(recipientIds)
            .build();

    kafkaTemplate.send("chat-notification-topic", event);
    log.info("Sent notification event to Kafka for room {}", room.getId());
  }

  private Map<String, Object> convertToMapPayload(ChatMessage msg) {
    Map<String, Object> map = new HashMap<>();
    map.put("id", msg.getId());
    map.put("roomId", msg.getRoomId());
    map.put("senderId", msg.getSenderId());
    map.put("senderName", msg.getSenderName());
    map.put("senderAvatar", msg.getSenderAvatar());
    map.put("content", msg.getContent());
    map.put("media", msg.getMedia());
    map.put("createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : null);
    map.put("isDeleted", msg.getIsDeleted());
    return map;
  }

  private MessageResponse mapToDto(ChatMessage msg) {
    MessageResponse dto = new MessageResponse();
    dto.setId(msg.getId());
    dto.setConversationId(msg.getRoomId());
    dto.setSenderId(msg.getSenderId());
    dto.setSenderName(msg.getSenderName());
    dto.setSenderAvatar(msg.getSenderAvatar());
    dto.setReplyToId(msg.getReplyToId());
    dto.setContent(msg.getContent());
    dto.setMedia(msg.getMedia());
    dto.setCreatedAt(msg.getCreatedAt());
    dto.setDeletedAt(msg.getDeletedAt());
    return dto;
  }
}
