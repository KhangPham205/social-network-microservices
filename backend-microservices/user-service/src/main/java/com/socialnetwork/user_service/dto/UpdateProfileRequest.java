package com.socialnetwork.user_service.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class UpdateProfileRequest {
  private String displayName;
  private String bio;
  private String favorites;
  private Instant dateOfBirth;
}
