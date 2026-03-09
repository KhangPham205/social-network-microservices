package com.socialnetwork.auth_service.dto;

import com.kt.social.auth.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String id;
    private String email;
    private AccountStatus status;
    private List<String> roles;
    private TokenResponse token;
}