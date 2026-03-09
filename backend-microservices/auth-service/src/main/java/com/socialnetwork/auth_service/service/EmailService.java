package com.socialnetwork.auth_service.service;


import com.socialnetwork.auth_service.enums.OtpType;
import com.socialnetwork.auth_service.model.UserCredential;

public interface EmailService {
    void sendVerificationEmail(String to, String subject, String body) throws MessageExce;
    void sendEmail(UserCredential user, OtpType otpType, String otp);
}
