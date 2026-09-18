package com.socialnetwork.notification_service.dto;

/** Public identity of the user who triggered a notification. */
public record ActorDto(Long id, String displayName, String avatarUrl) {}
