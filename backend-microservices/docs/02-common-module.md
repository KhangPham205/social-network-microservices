# 02 · Module `common`

Thư viện Java thuần (không có main class), artifact `com.socialnetwork:common`. Package gốc: **`com.socialnetwork.common`**. Được 6 business service phụ thuộc; `api-gateway` và `discovery-server` **không** dùng.

Nguyên tắc: `common` chỉ chứa **hợp đồng (contract) và hạ tầng cắt ngang**, không chứa nghiệp vụ. Các starter web/security/JPA khai báo `optional` — service tự khai báo starter mình cần.

## Cấu trúc

| Package | Nội dung | Ghi chú |
|---|---|---|
| `constants` | `ApiConstants` (prefix REST), `KafkaTopics` (tên topic), `WebSocketConstants` (destination STOMP), `SecurityConstants` (tên cookie, claim, header, role) | **Luôn** dùng constant thay literal |
| `vo` | `TargetType`, `FriendshipStatus`, `AccountStatus`, `NotificationType`, `ModerationAction`, `PageVO<T>`, `CursorPage<T>` | Enum dùng chung giữa service; lưu DB dạng `STRING` |
| `dto` | `IdCount` (projection), `UserSummary(id, displayName, avatarUrl)`, `MessageModerationView` | DTO trả về từ internal API |
| `events` | 12 record event Kafka (xem `04-kafka-events.md`) | Một class duy nhất cho mỗi event |
| `exception` | `ApiException` (base, mang `HttpStatus`) → `ResourceNotFoundException` 404, `BadRequestException` 400, `AccessDeniedException` 403, `InvalidCredentialsException` 401, `ConflictException` 409; `ErrorResponse`; `GlobalExceptionHandler` | Handler tự đăng ký vào mọi service (kể cả `@WebMvcTest`) |
| `security` | `JwtProperties`, `JwtValidator`, `JwtPrincipal`, `JwtAuthenticationFilter`, `JwtAuthenticationDetails`, `InternalApiProperties`, `InternalTokenAuthenticationFilter`, `InternalTokenInterceptor`, `BearerTokenRelayInterceptor`, `JsonAuthenticationEntryPoint`, `JsonAccessDeniedHandler`, `JwtSecurityConfigurer`, `SecurityUtils` | Xem `03-bao-mat.md` |
| `entity` | `BaseEntity` (id IDENTITY, `createdAt/updatedAt` Hibernate, `createdBy/updatedBy` JPA auditing, equals theo id) | Subclass **không** dùng `@Data`/`@EqualsAndHashCode` |
| `config` | `CommonWebAutoConfiguration`, `CommonSecurityAutoConfiguration`, `CommonJpaAuditingAutoConfiguration` | Đăng ký qua `META-INF/spring/…AutoConfiguration.imports` |

## Auto-configuration: service cần làm gì?

**Không cần gì cả** ngoài khai báo property. Không `scanBasePackages`, không `@Import`, không `@EnableCommonSecurity` (đã xoá).

Property bắt buộc trong mỗi service:

```yaml
jwt:
  secret: ${JWT_SECRET:dev-only-jwt-secret-change-me-0123456789abcdef}   # >= 32 ký tự, giống nhau ở mọi service
app:
  internal:
    token: ${INTERNAL_API_TOKEN:dev-only-internal-token-change-me}
```

Bean có sẵn để inject: `JwtValidator`, `JwtAuthenticationFilter`, `InternalTokenAuthenticationFilter`, `JwtSecurityConfigurer`, `InternalTokenInterceptor`, `BearerTokenRelayInterceptor`, `JsonAuthenticationEntryPoint`, `JsonAccessDeniedHandler`, `JwtProperties`, `InternalApiProperties`, `AuditorAware<String> commonAuditorAware`.

## Xử lý lỗi thống nhất

Mọi service trả về cùng một body lỗi:

```json
{ "statusCode": 403, "error": "Forbidden", "message": "...", "path": "/api/v1/...", "timestamp": "2026-09-18T04:00:00Z", "details": {"field": "msg"} }
```

`GlobalExceptionHandler` map: `ApiException` → status của nó; `MethodArgumentNotValidException`/`ConstraintViolationException` → 400 kèm `details`; type mismatch/missing param/body không đọc được → 400; `NoResourceFoundException` → 404; `HttpRequestMethodNotSupported` → 405; `DataIntegrityViolationException` → 409; Spring Security `AccessDeniedException` → 403, `AuthenticationException` → 401; `HttpStatusCodeException` từ RestClient → 404 nếu downstream 404, còn lại 502; `ResourceAccessException` → 503; exception lạ → 500 với message chung (log ERROR kèm stack trace, **không** lộ message nội bộ). `@ResponseStatus` trên exception tự viết vẫn được tôn trọng.

Quy ước trong service: **ném exception của `common`**, không ném `RuntimeException`/`IllegalArgumentException` cho lỗi nghiệp vụ dự kiến; controller không `try/catch` để trả 400 thủ công.

## `SecurityUtils` (static)

| Hàm | Ý nghĩa |
|---|---|
| `getCurrentUserId()` | userId hiện tại, ném 403 nếu anonymous |
| `findCurrentUserId()` | `Optional<Long>` |
| `getCurrentToken()` | raw JWT của request (để relay) |
| `hasRole("ADMIN")` / `hasAuthority("REPORT:CREATE")` | kiểm tra authority từ claim `roles` |
| `isAuthenticated()` | |

## `PageVO<T>`

Envelope phân trang chuẩn: `page, size, totalElements, totalPages, numberOfElements, content`. Tạo bằng `PageVO.from(page)` hoặc `PageVO.from(page, mapper)`.

## Kiểm thử

`common` có unit test cho `JwtValidator`, `JwtAuthenticationFilter`, `InternalTokenAuthenticationFilter`, `GlobalExceptionHandler` (`mvn -pl common test`). Khi thay đổi contract event: sửa ở `common` trước, `mvn -pl common install`, rồi compile toàn reactor để tìm điểm vỡ.
