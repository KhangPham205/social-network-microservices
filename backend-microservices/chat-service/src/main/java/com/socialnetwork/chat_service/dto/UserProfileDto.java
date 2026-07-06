package com.socialnetwork.chat_service.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
  private Long id;
  private String displayName;
  private String avatarUrl;
  private String bio;
  private String favorites;
  private Instant dateOfBirth;
  private Instant joinedAt;
}
