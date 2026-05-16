package com.socialnetwork.moderation_service.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
public class MessageSentEvent extends ApplicationEvent {
    private final String id;
    private final Long conversationId;
    private final String content;
    private final Long senderId;
    private final List<Map<String, Object>> media;
    private final Instant createdAt = Instant.now();

    public MessageSentEvent(Object source, String messageId, Long conversationId, String content, Long senderId, List<Map<String, Object>> media) {
        super(source);
        this.id = messageId;
        this.conversationId = conversationId;
        this.content = content;
        this.senderId = senderId;
        this.media = media;
    }
}
