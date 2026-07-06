package com.socialnetwork.auth_service.dto;

import lombok.Data;

@Data
public class CreateStaffRequest {
  private String username;
  private String password;
  private String email;
  private String fullname;
  private String roleName; // Tên của Role (ví dụ: "ADMIN" hoặc "MODERATOR")
}
