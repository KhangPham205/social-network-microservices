package com.socialnetwork.auth_service.model;

import com.socialnetwork.common.vo.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_credential")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCredential {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  private String username;

  @Column(nullable = false)
  private String password;

  @Column(unique = true)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private AccountStatus status = AccountStatus.PENDING;

  /** Pending e-mail verification OTP; null when none is outstanding. */
  private String verificationCode;

  private Instant verificationCodeExpiry;

  /** Wrong codes submitted for the current verification OTP (nullable for legacy rows). */
  private Integer verificationAttempts;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  @Builder.Default
  private Set<Role> roles = new HashSet<>();

  public int getVerificationAttempts() {
    return verificationAttempts == null ? 0 : verificationAttempts;
  }
}
