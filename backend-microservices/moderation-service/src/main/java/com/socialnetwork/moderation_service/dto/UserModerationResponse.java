package com.socialnetwork.moderation_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserModerationResponse {
    private Long userId;
    private String username;
    private String email;
    private String displayName;
    private String avatar;
    private String status; // ACTIVE, BLOCKED, SUSPENDED, etc.
    private long violationCount;

    // Constructor matching query result order
    public UserModerationResponse(Long userId,
                                  String username,
                                  String email,
                                  String displayName,
                                  String avatar,
                                  String status,
                                  Long violationCount) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.avatar = avatar;
        this.status = status;
        this.violationCount = violationCount != null ? violationCount : 0L;
    }
}