# 03 · Bảo mật & xác thực

## Mô hình

- **Issuer**: auth-service phát JWT **HS256** (`JwtProvider`), thời hạn `jwt.expiration` (ms, mặc định 1 ngày). Claims: `sub` = username, `userId` (Long), `roles` = danh sách authority gồm `ROLE_<role>` (ví dụ `ROLE_ADMIN`, `ROLE_USER`) **và** tên permission (`REPORT:CREATE`, `USER:BLOCK`, …).
- **Resource server**: mỗi business service tự xác thực bằng `JwtAuthenticationFilter` của `common` với **cùng secret** (`JWT_SECRET`). Principal name = `userId` (String); authorities = claim `roles`. Vì vậy `hasRole("ADMIN")`, `@PreAuthorize("hasAuthority('X')")`, `SecurityUtils.hasRole(...)` hoạt động ở mọi service.
- **Gateway** không xác thực, không thêm header danh tính; nó chỉ: route, CORS, **chặn 404 mọi path chứa `/internal/`**, timeout.
- **Token được đọc từ**: header `Authorization: Bearer …` trước, sau đó cookie `jwt`. Cookie do auth-service set: `HttpOnly; Secure; SameSite=None` (frontend khác origin).
- **Refresh token**: UUID lưu DB (`refresh_token`), cookie `refreshToken`, thời hạn `jwt.refresh.expiration` (ms). Xoay vòng (rotate) mỗi lần refresh; thu hồi toàn bộ khi logout, đổi mật khẩu, bị BLOCKED.
- **Trạng thái tài khoản** (`AccountStatus`): `WAITING` → `PENDING` → `ACTIVE`; `BLOCKED`, `NOT_SOLVED`. Chỉ `ACTIVE` mới login/refresh được.

## Endpoint public (không cần JWT)

| Service | Path |
|---|---|
| auth | `POST /api/v1/auth/{register,login,logout,refresh,sendVerifyEmail,resendVerifyEmail,reset-password,verify-otp}` |
| tất cả | `OPTIONS /**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/error`, `/actuator/health/**` |
| chat, notification | handshake `/ws/**` (JWT kiểm tra trong `JwtHandshakeInterceptor`) |

Mọi thứ khác: `authenticated()`. Admin: `/api/v1/<svc>/admin/**` → `hasRole("ADMIN")` + `@PreAuthorize` theo permission ở moderation.

## Internal API (service ↔ service)

- Đường dẫn: `/api/v1/<svc>/internal/**`.
- Bảo vệ: `InternalTokenAuthenticationFilter` (chạy trước JWT filter) cấp `ROLE_INTERNAL` khi header `X-Internal-Token` khớp `app.internal.token`; `JwtSecurityConfigurer` tự thêm rule `/api/v1/*/internal/**` → `hasRole("INTERNAL")`.
- Phía gọi: `RestClient.Builder` `@LoadBalanced` + `InternalTokenInterceptor` (bean sẵn). Không relay JWT người dùng sang internal API; `BearerTokenRelayInterceptor` chỉ dùng khi thật sự cần hành động "thay mặt user" tới endpoint public của service khác.
- Gateway trả 404 cho mọi request chứa `/internal/` → internal API không thể gọi từ Internet.

Bảng endpoint internal đầy đủ: xem `docs/services/*.md` (mục *Internal API*).

## WebSocket (STOMP)

- Endpoint: chat `/ws/chat`, notification `/ws/notification`; gateway route `lb:ws://`.
- Handshake nhận JWT từ cookie `jwt`, header `Authorization: Bearer` hoặc query `?token=`; origin giới hạn theo `CORS_ALLOWED_ORIGINS`.
- `ChannelInterceptor` kiểm tra SUBSCRIBE: chat chỉ cho phép `/queue|/topic/conversation/{roomId}` khi là thành viên; notification chỉ cho phép `/user/queue/**` của chính mình và `/topic/public`.
- Broker: simple broker in-memory (giới hạn 1 instance/service).

## Chuẩn `SecurityConfig` trong service

```java
@Configuration @EnableWebSecurity @EnableMethodSecurity @RequiredArgsConstructor
public class SecurityConfig {
  private final JwtSecurityConfigurer jwtSecurity;
  @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    jwtSecurity.apply(http)   // csrf off, stateless, JSON 401/403, internal-token filter, jwt filter, rule /internal/**
        .authorizeHttpRequests(a -> a
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(ApiConstants.SWAGGER_WHITELIST).permitAll()
            .requestMatchers("/error", "/actuator/health/**").permitAll()
            .requestMatchers("/api/v1/<svc>/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated());
    return http.build();
  }
}
```

## Secret & cấu hình nhạy cảm

- Không còn secret nào trong repo. Dev default (`dev-only-…`) chỉ dùng local; prod bắt buộc set `JWT_SECRET`, `INTERNAL_API_TOKEN`, `DB_PASSWORD`, `MAIL_PASSWORD`, … qua env (profile `prod` không có default).
- **Việc cần làm ngoài code (đã báo user)**: thu hồi Gmail app password và JWT secret cũ từng nằm trong repo/lịch sử git.

## Những gì cố ý chưa làm

- Gateway chưa xác thực JWT tập trung (giữ mô hình mỗi service tự validate để không phải viết filter reactive); có thể thêm sau bằng Spring Security WebFlux.
- Eureka và ai-service không có auth — chỉ chạy trong mạng nội bộ.
- Chưa rate-limit login/OTP ở gateway (chỉ giới hạn số lần thử OTP trong auth-service).
