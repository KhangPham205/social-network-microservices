package com.socialnetwork.auth_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kt.social.auth.enums.AccountStatus;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.common.constants.ApiConstants;
import com.kt.social.common.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserDetailsService userDetailsService;
    private final UserCredentialRepository userCredentialRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/ws")) { // ⚡ Bỏ qua WebSocket handshake
            filterChain.doFilter(request, response);
            return;
        }

        // Lấy token từ header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String username = jwtProvider.extractUsername(token);

        // Nếu có username và chưa được xác thực
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtProvider.isTokenValid(token, userDetails)) {

                Long userId = jwtProvider.extractUserId(token);

                Optional<UserCredential> userOpt = userCredentialRepository.findById(userId);

                if (userOpt.isPresent() && userOpt.get().getStatus() == AccountStatus.BLOCKED) {
                    // Kiểm tra nếu KHÔNG phải là API tạo khiếu nại
                    boolean isComplaintApi = path.startsWith(ApiConstants.COMPLAINTS)
                            && request.getMethod().equals(HttpMethod.POST.name());

                    if (!isComplaintApi) {
                        sendBlockResponse(response);
                        return; // Dừng, không cho đi tiếp
                    }
                }

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userId.toString(),
                                null,
                                userDetails.getAuthorities()
                        );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Đặt user đã xác thực vào SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    // Helper function để trả về lỗi JSON
    private void sendBlockResponse(HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Tài khoản của bạn đã bị khóa. Bạn chỉ có thể truy cập chức năng khiếu nại.",
                new Date().getTime()
        );

        OutputStream out = response.getOutputStream();
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(out, errorResponse);
        out.flush();
    }
}