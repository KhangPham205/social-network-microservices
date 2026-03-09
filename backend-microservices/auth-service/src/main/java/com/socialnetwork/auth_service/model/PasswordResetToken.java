package com.socialnetwork.auth_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String newPassword;

    @OneToOne
    @JoinColumn(name = "user_credential_id", referencedColumnName = "id")
    private UserCredential userCredential;

    @Column(nullable = false)
    private Instant expiryDate;
}
