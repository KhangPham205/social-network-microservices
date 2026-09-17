package com.socialnetwork.media_service.events;

import java.util.List;
import java.util.Map;

public record ContentCreatedEvent(
    Long contentId,
    String contentType,
    String content,
    Long creatorId,
    List<Map<String, String>> media) {}
