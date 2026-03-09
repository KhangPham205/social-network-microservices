package com.socialnetwork.auth_service.security;

import com.kt.social.auth.model.Role;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserCredentialRepository userCredentialRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserCredential user = userCredentialRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Set<Role> roles = user.getRoles();
        Set<String> roleNames = new HashSet<>();
        Set<String> permissionNames = new HashSet<>();

        for (Role role : roles) {
            roleNames.add(role.getName().replace("ROLE_", ""));

            role.getPermissions().forEach(permission ->
                    permissionNames.add(permission.getName())
            );
        }

        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(roleNames.toArray(new String[0]))
                .authorities(permissionNames.toArray(new String[0]))
                .build();
    }
}
