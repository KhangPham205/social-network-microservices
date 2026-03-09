package com.socialnetwork.auth_service.service.impl;

import com.kt.social.auth.enums.OtpType;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.service.EmailService;
import com.kt.social.common.exception.BadRequestException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender emailSender;

    @Override
    public void sendVerificationEmail(String to, String subject, String body) throws MessagingException {
        MimeMessage mimeMessage = emailSender.createMimeMessage();
        MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, true);

        // Set email parameters
        mimeMessageHelper.setTo(to);
        mimeMessageHelper.setSubject(subject);
        mimeMessageHelper.setText(body, true); // true indicates HTML content

        // Send the email
        emailSender.send(mimeMessage);
    }

    @Override
    public void sendEmail(UserCredential user, OtpType otpType, String otp){
        String subject;
        String verificationCode;

        switch (otpType) {
            case VERIFY_EMAIL -> {
                subject = "Account Verification";
                verificationCode = otp;
            }
            case RESET_PASSWORD -> {
                subject = "Reset Your Password";
                verificationCode = otp;
            }
            default -> throw new BadRequestException("Invalid OTP type");
        }

        String htmlMessage = "<html>"
                + "<body style=\"font-family: Arial, sans-serif;\">"
                + "<div style=\"background-color: #f5f5f5; padding: 20px;\">"
                + "<h2 style=\"color: #333;\">Welcome to our app!</h2>"
                + "<p style=\"font-size: 16px;\">Please enter the verification code below to continue:</p>"
                + "<div style=\"background-color: #fff; padding: 20px; border-radius: 5px; box-shadow: 0 0 10px rgba(0,0,0,0.1);\">"
                + "<h3 style=\"color: #333;\">Verification Code:</h3>"
                + "<p style=\"font-size: 18px; font-weight: bold; color: #007bff;\">" + verificationCode + "</p>"
                + "</div>"
                + "</div>"
                + "</body>"
                + "</html>";

        try {
            sendVerificationEmail(user.getEmail(), subject, htmlMessage);
        } catch (MessagingException e) {
            throw new BadRequestException("Failed to send verification email: " + e.getMessage());
        }
    }
}
