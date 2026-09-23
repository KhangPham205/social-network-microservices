package com.socialnetwork.chat_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One attachment already uploaded to media-service; chat-service only stores the descriptor.
 *
 * @param type MIME type or coarse kind ("image", "video", ...)
 */
public record MediaItem(
    @NotBlank @Size(max = 2048) String url,
    @Size(max = 100) String type,
    @Size(max = 255) String name) {}
