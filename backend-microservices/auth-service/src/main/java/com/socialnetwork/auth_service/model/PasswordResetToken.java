package com.socialnetwork.auth_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One pending password reset per e-mail: OTP code + the BCrypt hash of the requested password. */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String email;

  @Column(nullable = false)
  private String code;

  @Column(nullable = false)
  private String newPassword;

  @Column(nullable = false)
  private Instant expiryDate;

  /** Wrong codes submitted for this token (nullable for legacy rows). */
  private Integer attempts;

  public int getAttempts() {
    return attempts == null ? 0 : attempts;
  }

  public boolean isExpired() {
    return expiryDate.isBefore(Instant.now());
  }
}
