package com.socialnetwork.notification_service.dto;

/** Unread counter pushed to /user/queue/notification-summary and returned by GET /unread-count. */
public record NotificationCountDto(long unreadCount) {}
