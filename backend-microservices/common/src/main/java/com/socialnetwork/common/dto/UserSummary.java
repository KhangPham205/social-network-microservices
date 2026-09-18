package com.socialnetwork.common.dto;

/** Minimal public identity of a user, returned by user-service internal batch endpoints. */
public record UserSummary(Long id, String displayName, String avatarUrl) {}
