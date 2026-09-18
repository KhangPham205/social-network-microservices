package com.socialnetwork.auth_service.service.impl;

import com.socialnetwork.auth_service.enums.OtpType;
import com.socialnetwork.auth_service.exception.MailDeliveryException;
import com.socialnetwork.auth_service.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

  private final JavaMailSender mailSender;

  @Override
  public void sendOtp(String to, OtpType otpType, String otp) {
    String subject =
        switch (otpType) {
          case VERIFY_EMAIL -> "Account Verification";
          case RESET_PASSWORD -> "Reset Your Password";
        };
    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(htmlBody(otp), true);
      mailSender.send(mimeMessage);
    } catch (MessagingException | MailException e) {
      log.error("Failed to send {} e-mail", otpType, e);
      throw new MailDeliveryException("E-mail service is unavailable, please try again later");
    }
  }

  private static String htmlBody(String code) {
    return """
        <html><body style="font-family: Arial, sans-serif;">
          <div style="background-color: #f5f5f5; padding: 20px;">
            <h2 style="color: #333;">Welcome to our app!</h2>
            <p style="font-size: 16px;">Please enter the verification code below to continue:</p>
            <div style="background-color: #fff; padding: 20px; border-radius: 5px; box-shadow: 0 0 10px rgba(0,0,0,0.1);">
              <h3 style="color: #333;">Verification Code:</h3>
              <p style="font-size: 18px; font-weight: bold; color: #007bff;">%s</p>
            </div>
          </div>
        </body></html>
        """
        .formatted(code);
  }
}
