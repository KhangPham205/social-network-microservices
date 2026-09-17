package com.socialnetwork.chat_service.event;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageNotificationEvent {
  private Long roomId;
  private Long senderId;
  private String senderName;
  private String previewContent;
  private List<Long> recipientIds;
}
