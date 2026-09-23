package com.socialnetwork.chat_service.mapper;

import com.socialnetwork.chat_service.dto.MessageResponse;
import com.socialnetwork.chat_service.enums.MessageType;
import com.socialnetwork.chat_service.model.ChatMessage;
import com.socialnetwork.common.dto.MessageModerationView;
import java.util.List;

/** Maps the Mongo document to the shapes the clients and moderation-service see. */
public final class MessageMapper {

  /** Shown instead of the body of a message the sender removed or moderation blocked. */
  public static final String REVOKED_CONTENT = "Tin nhắn đã bị gỡ bỏ";

  private MessageMapper() {}

  public static MessageResponse toResponse(ChatMessage message) {
    boolean hidden = message.isHidden();
    return MessageResponse.builder()
        .id(message.getId())
        .conversationId(message.getRoomId())
        .senderId(message.getSenderId())
        .senderName(message.getSenderName())
        .senderAvatar(message.getSenderAvatar())
        .replyToId(message.getReplyToId())
        .content(hidden ? REVOKED_CONTENT : message.getContent())
        .type(hidden ? MessageType.SYSTEM : typeOf(message))
        .media(hidden ? List.of() : message.getMedia())
        .createdAt(message.getCreatedAt())
        .readBy(message.getReadBy() == null ? List.of() : List.copyOf(message.getReadBy()))
        .deleted(hidden)
        .deletedAt(message.getDeletedAt())
        .build();
  }

  /** Moderation sees the raw content, that is the whole point of the review. */
  public static MessageModerationView toModerationView(ChatMessage message) {
    return new MessageModerationView(
        message.getId(),
        message.getRoomId(),
        message.getSenderId(),
        message.getContent(),
        message.getCreatedAt(),
        Boolean.TRUE.equals(message.getIsSystemBan()));
  }

  private static MessageType typeOf(ChatMessage message) {
    return message.getType() == null ? MessageType.TEXT : message.getType();
  }
}
