package com.socialnetwork.auth_service.util;

import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Tiện ích lấy user hiện tại (dùng được cả trong REST + Service).
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<String> getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.ofNullable(auth.getName());
    }

    public static Optional<UserCredential> getCurrentUserCredential(UserCredentialRepository repo) {
        return getCurrentUsername().flatMap(repo::findByUsername);
    }

    public static Long getCurrentUserId(UserCredentialRepository repo) {
        String username = getCurrentUsername()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
        return repo.findByUsername(username)
                .map(UserCredential::getId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public static User getCurrentUser(
            UserCredentialRepository credRepo,
            UserRepository userRepo
    ) {
        String id = getCurrentUsername()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));

        UserCredential cred = credRepo.findById(Long.parseLong(id))
                .orElseThrow(() -> new RuntimeException("Credential not found"));

        return userRepo.findByCredential(cred);
    }
}