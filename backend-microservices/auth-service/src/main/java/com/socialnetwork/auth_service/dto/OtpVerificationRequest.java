package com.socialnetwork.auth_service.dto;

import com.socialnetwork.auth_service.enums.OtpType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpVerificationRequest {
  @NotBlank @Email private String email;

  @NotBlank private String code;

  @NotBlank
  @Enumerated(EnumType.STRING)
  private OtpType type;
}
