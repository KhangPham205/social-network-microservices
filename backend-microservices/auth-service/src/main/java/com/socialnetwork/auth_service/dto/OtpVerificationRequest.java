package com.socialnetwork.auth_service.dto;

import com.socialnetwork.auth_service.enums.OtpType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OtpVerificationRequest {
  @NotBlank @Email private String email;

  @NotBlank private String code;

  @NotNull private OtpType type;
}
