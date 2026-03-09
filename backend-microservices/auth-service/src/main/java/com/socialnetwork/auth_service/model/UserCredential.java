package com.socialnetwork.auth_service.model;

import com.kt.social.auth.enums.AccountStatus;
import com.kt.social.domain.user.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "user_credential")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class  UserCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true)
    private String email;

//    @Column(unique = true)
//    private String phone;

//    @ManyToOne
//    @JoinColumn(name = "role_id", nullable = false)
//    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.PENDING;

    private String verificationCode; // mã xác thực email

    private Instant verificationCodeExpiry; // thời gian hết hạn mã xác thực

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    // Quan hệ 1-1 với bảng User (thông tin cá nhân)
    @OneToOne(mappedBy = "credential")
    private User user;
}
