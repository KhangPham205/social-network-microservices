package com.socialnetwork.moderation_service.dto;

import com.socialnetwork.common.vo.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateComplaintRequest {

  @NotBlank private String targetId;

  @NotNull private TargetType targetType;

  @NotBlank private String reason;
}
