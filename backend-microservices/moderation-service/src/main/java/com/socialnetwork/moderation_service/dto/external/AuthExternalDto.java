package com.socialnetwork.moderation_service.dto.external;

import lombok.Data;

@Data
public class AuthExternalDto {
  private Long id;
  private String username;
  private String email;
  private String status;
}
