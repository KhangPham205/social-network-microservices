# auth-service (port 8081)

Nhà cung cấp danh tính của hệ thống. Sở hữu credential (username/password/email), RBAC (role + permission), phát JWT và refresh token, xử lý OTP email, và khởi động saga đăng ký.

Đây là service duy nhất **phát hành** token; các service khác chỉ **xác thực** token bằng `common` (xem `docs/03-bao-mat.md`).

## Runtime

| | |
|---|---|
| Port | `${SERVER_PORT:8081}` |
| Database | PostgreSQL `auth_db` (`${DB_URL:jdbc:postgresql://localhost:5432/auth_db}`) |
| Kafka | producer + consumer, group `auth-service` |
| SMTP | `spring.mail.*`, `${MAIL_USERNAME}`/`${MAIL_PASSWORD}` (không có default, prod bắt buộc) |
| Gọi ra ngoài | user-service `POST /api/v1/users/internal/create` (tạo profile cho tài khoản staff) |

## Domain model

| Bảng | Field chính |
|---|---|
| `user_credential` | `id`, `username` (unique), `password` (BCrypt), `email` (unique), `status` (`AccountStatus`, STRING), `verification_code` + `verification_code_expiry` + `verification_attempts`, `roles` (ManyToMany EAGER qua `user_roles`) |
| `roles` | `id`, `name` (unique, không có tiền tố `ROLE_`: "USER", "ADMIN"), `description`, `permissions` (ManyToMany EAGER qua `role_permissions`) |
| `permissions` | `id`, `resource`, `action`, `name` unique = `RESOURCE:ACTION`, unique(resource, action) |
| `refresh_token` | `id`, `token` (UUID, unique), `expiry_date`, `user` (ManyToOne LAZY) |
| `password_reset_tokens` | `id`, `email`, `code` (6 số), `new_password` (BCrypt, lưu trước khi xác minh), `expiry_date`, `attempts` |

Vòng đời trạng thái: `register` → **WAITING** → (saga thành công) **PENDING** → (xác minh OTP) **ACTIVE**. Saga lỗi → **NOT_SOLVED** (không xoá cứng tài khoản nữa). Moderation khoá → **BLOCKED**.

## REST API

### Công khai

| Method | Path | Mô tả |
|---|---|---|
| POST | `/api/v1/auth/register` | Tạo credential (WAITING, role USER); kiểm tra trùng **cả username và email** → 409 |
| POST | `/api/v1/auth/login` | Trả `LoginResponse` + set cookie `jwt` và `refreshToken`. Sai mật khẩu **hoặc** sai username đều trả 401 cùng một thông báo (chống dò tài khoản). Tài khoản khác ACTIVE → 403 |
| POST | `/api/v1/auth/logout` | Thu hồi refresh token, xoá cookie. Không lỗi khi access token đã hết hạn |
| POST | `/api/v1/auth/refresh` | Xoay vòng refresh token (xoá cũ, phát mới) + access token mới; chặn tài khoản khác ACTIVE |
| POST | `/api/v1/auth/sendVerifyEmail` · `/resendVerifyEmail` | Gửi OTP 6 số |
| POST | `/api/v1/auth/reset-password` | Lưu mật khẩu mới (đã BCrypt) + OTP; không cho yêu cầu thứ hai khi còn hiệu lực |
| POST | `/api/v1/auth/verify-otp` | `type=VERIFY_EMAIL` → ACTIVE; `type=RESET_PASSWORD` → đổi mật khẩu + thu hồi toàn bộ refresh token. Tối đa **5 lần thử** mỗi mã |

### Admin (`hasRole("ADMIN")`)

| Method | Path | Mô tả |
|---|---|---|
| POST | `/api/v1/auth/admin/staff` | Tạo tài khoản nhân viên với role chỉ định; gọi user-service tạo profile, thất bại thì rollback |
| GET | `/api/v1/auth/admin/roles` | Danh sách role (`RoleResponse`) |
| POST · DELETE | `/api/v1/auth/admin/roles/{roleId}/permissions/{permissionId}` | Gán / gỡ permission |
| POST · GET · PUT · DELETE | `/api/v1/auth/admin/permissions[/{id}]` | CRUD permission |

> Trước refactor các controller này nằm ở `/api/v1/admin/**`, mà gateway chỉ route `/api/v1/auth/**` — nghĩa là toàn bộ màn hình quản trị RBAC **không gọi được** qua gateway. Đã chuyển về dưới `/api/v1/auth/admin`.

### Internal (`X-Internal-Token`, gateway chặn 404)

| Method | Path | Request → Response |
|---|---|---|
| GET | `/api/v1/auth/internal/credentials/{id}` | → `AuthCredentialDto{id, username, email, status, roles}` |
| POST | `/api/v1/auth/internal/credentials/batch` | `List<Long>` → `List<AuthCredentialDto>` |
| PUT | `/api/v1/auth/internal/credentials/{id}` | `UpdateRoleStatusRequest{status, roles}` → 204 |

## JWT

`JwtProvider` ký HS256 với `jwt.secret`. Claims: `sub` = username, `userId` (Long), `roles`. **`roles` chứa `ROLE_<tên role>` cho mỗi role cộng với tên từng permission** (ví dụ `["ROLE_ADMIN", "USER:BLOCK", "REPORT:PROCESS"]`) — do `AuthorityMapper` sinh ra và dùng chung cho login, refresh và `CustomUserDetailsService`.

Trước refactor, `User.builder().roles(...).authorities(...)` khiến `authorities(...)` ghi đè danh sách role, nên claim không bao giờ có `ROLE_*`: `hasRole("ADMIN")` luôn sai ở mọi service. Đây là gốc rễ của việc phân quyền không hoạt động toàn hệ thống.

Refresh token: UUID trong DB, TTL đọc từ `jwt.refresh.expiration` tính bằng **mili giây** (bản cũ hiểu nhầm là giây nên token sống ~19 năm). Bị thu hồi khi: logout, đổi mật khẩu thành công, tài khoản rời trạng thái ACTIVE.

## Kafka

| Chiều | Topic | Event | Xử lý |
|---|---|---|---|
| Publish | `USER_CREATED` | `UserCreatedEvent` | Phát **sau khi transaction register commit** (`UserCreatedEventPublisher`), key = accountId |
| Consume | `PROFILE_CREATED` | `ProfileCreatedEvent` | `WAITING → PENDING`, chỉ khi còn WAITING (idempotent) |
| Consume | `PROFILE_FAILED` | `ProfileFailedEvent` | Đặt `NOT_SOLVED`. Bản cũ `deleteById` — xoá cứng tài khoản khi event bị gửi lại |
| Consume | `USER_MODERATION_ACTIONS` | `UserModerationEvent` | Đổi `AccountStatus`; nếu BLOCKED thì thu hồi refresh token |

Error handler: `DefaultErrorHandler` 3 lần thử, back-off 1s, rồi `DeadLetterPublishingRecoverer` → `<topic>.DLT`.

> Lưu ý vận hành: consumer group đổi từ `auth-service-group-v2` sang `auth-service`, nên lần deploy đầu tiên listener đọc lại từ `earliest`.

## Bảo mật

`SecurityConfig` dựng trên `JwtSecurityConfigurer` + `@EnableMethodSecurity`. permitAll: 8 endpoint POST công khai ở trên (giới hạn theo method), swagger, `/error`, `/actuator/health/**`, OPTIONS. `/api/v1/auth/admin/**` → `hasRole("ADMIN")`. `/api/v1/auth/internal/**` → `hasRole("INTERNAL")` (do configurer thêm). Còn lại `authenticated()`.

Trước refactor toàn bộ `/api/v1/auth/**` là `permitAll`, nghĩa là bất kỳ ai cũng gọi được `PUT /internal/credentials/{id}` để tự cấp quyền ADMIN.

## Test

`mvn -pl auth-service test` → **50 test xanh**, chạy offline (H2, Eureka tắt, Kafka listener không tự khởi động).

- `AdminAuthControllerTest`: anonymous → 401, USER → 403, ADMIN → 200, có permission nhưng thiếu role → 403, body sai → 400.
- Unit test: `AuthorityMapperTest`, `JwtProviderTest` (round-trip kiểm bằng `JwtValidator` của common), `AuthServiceImplTest` (login từ chối mọi trạng thái khác ACTIVE), `RefreshTokenServiceImplTest` (xoay vòng, TTL ms), `AuthSagaCompensatorTest` (idempotent hai chiều).

**Lưu ý cho người viết test mới**: Spring Boot 4.0.3 không còn tự gắn cấu hình security của Spring Security vào slice `@WebMvcTest`, nên `@WithMockUser` một mình bị bỏ qua và mọi request trả 401. Cách dùng trong repo: thêm `.with(testSecurityContext())` vào request builder.
