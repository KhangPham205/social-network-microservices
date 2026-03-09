package com.socialnetwork.auth_service.service;

import com.kt.social.auth.dto.PasswordResetRequest;

public interface PasswordResetService {
    void sendResetCode(PasswordResetRequest request);
}
