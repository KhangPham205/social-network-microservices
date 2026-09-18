package com.socialnetwork.notification_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Notification as seen by the client (REST list and STOMP push share this shape). */
public record NotificationDto(
    Long id,
    ActorDto actor,
    String content,
    String link,
    @JsonProperty("isRead") boolean read,
    Instant createdAt) {}
