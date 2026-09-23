# moderation-service (port 8086)

Service an toàn & kiểm duyệt. Lưu báo cáo vi phạm (report), khiếu nại (complaint) và nhật ký kiểm duyệt; chạy pipeline AI tự động cho bài viết, bình luận và tin nhắn; cung cấp API tổng hợp cho màn hình quản trị.

## Runtime

| | |
|---|---|
| Port | `${SERVER_PORT:8086}` |
| PostgreSQL | `moderation_db` (user mặc định `root`/`password` như các service khác) — `reports`, `complaints`, `moderation_logs` |
| Kafka | group `moderation-service` |
| ai-service | `${AI_SERVICE_URL:http://localhost:8000}`, connect 3s / read 30s |
| Gọi ra ngoài | auth, user, media, chat qua internal API |

## Domain model

| Bảng | Field chính |
|---|---|
| `reports` | kế thừa `BaseEntity`; `reporter_id` (**nullable** — null khi do hệ thống tạo), `source` (`ReportSource` USER/SYSTEM), `status` (`ReportStatus`, STRING), `target_type` (`TargetType`), `target_id` (String), `target_user_id`, `reason` (`ReportReason`, có thêm **`AI_DETECTED`**), `custom_reason`, `banned_by_system` (boolean) |
| `complaints` | kế thừa `BaseEntity`; `status` (`ComplaintStatus`, STRING), `target_type`, `target_id`, `user_id`, `content`, `admin_response` |
| `moderation_logs` | kế thừa `BaseEntity`; `target_type`, `target_id`, `action` (**`ModerationLogAction`**: AUTO_BAN, ADMIN_BLOCK, ADMIN_UNBLOCK, USER_BLOCK, USER_UNBLOCK), `reason`, `actor_id`, `report_id`, `complaint_id` |

Bản cũ lưu `status` **không có `@Enumerated`** nên Hibernate ghi số thứ tự enum vào DB: chèn thêm một hằng số ở giữa enum là toàn bộ dữ liệu cũ đổi nghĩa. Nay tất cả lưu dạng STRING. Sentinel `reporterId = -1` được thay bằng `reporter_id` null + `source = SYSTEM`.

## REST API

Tất cả dưới `/api/v1/moderation`. Mỗi endpoint có `@PreAuthorize` riêng, dạng `hasRole('ADMIN') or hasAuthority('<PERMISSION>')`.

| Method | Path | Quyền |
|---|---|---|
| POST | `/reports` | `isAuthenticated()` — cấm tự báo cáo chính mình |
| PUT | `/reports` | `REPORT:PROCESS` — duyệt thì publish BLOCK và ghi log kèm `reportId` |
| GET | `/reports/{id}` · `/reports?filter=` | `REPORT:VIEW_ALL` |
| POST | `/complaints` | `isAuthenticated()` — kiểm tra quyền sở hữu, **fail closed** |
| PUT | `/complaints/{id}` | `COMPLAINT:PROCESS` — duyệt thì publish UNBLOCK và ghi log kèm `complaintId` |
| GET | `/complaints[/{id}]` | `COMPLAINT:VIEW_ALL` |
| GET | `/users/{id}` · `/users/{id}/violations` | `USER:READ_SENSITIVE` hoặc `MODERATION:ACCESS` |
| GET | `/users` · `/posts/{id}` · `/comments/{id}` · `/posts/flagged` · `/comments/flagged` · `/messages/flagged` · `/history` · `/{type}/{id}/history` · `/{type}/{id}/reports` · `/{type}/{id}/complaints` | `MODERATION:ACCESS` |
| GET | `/messages/{id}` | `MESSAGE:READ_ANY` hoặc `MODERATION:ACCESS` |
| PUT | `/users/{id}/block` · `/unblock` | `USER:BLOCK` hoặc `MODERATION:ACCESS` |
| PUT | `/{type}/{id}/block` · `/unblock` | `POST:DELETE_ANY` hoặc `MODERATION:ACCESS` |

`GET /messages/flagged/grouped` đã bị xoá (nó luôn ném `UnsupportedOperationException`).

> **Bug bảo mật nghiêm trọng nhất của service này**: bản cũ thiếu `@EnableMethodSecurity`, nên **mọi `@PreAuthorize` đều bị bỏ qua** và bất kỳ người dùng đã đăng nhập nào cũng khoá được tài khoản người khác, chặn nội dung, và đọc email của mọi người. Nay đã bật; phần authority trong JWT cũng đã được auth-service điền đúng.

## Client nội bộ

| Client | Đường dẫn gọi |
|---|---|
| `AuthClient` | `/api/v1/auth/internal/credentials/{id}`, `/credentials/batch` |
| `UserClient` | `/api/v1/users/internal/{id}/admin-detail`, `/internal/batch`, `/internal/summaries` |
| `MediaClient` | `/api/v1/media/internal/posts/{id}/owner-id`, `/internal/posts/batch`, `/internal/comments/{id}/owner-id`, `/internal/comments/batch` |
| `ChatClient` | `/api/v1/chat/internal/messages/{id}`, `/internal/messages/{id}/owner-id`, `/internal/messages/batch` |

Tất cả dùng một `@LoadBalanced RestClient.Builder` + `InternalTokenInterceptor`. Bản cũ sao chép cookie `jwt` của người gọi sang lời gọi nội bộ — không hoạt động khi client dùng header Bearer, và `ChatClient` trỏ tới **ba endpoint không tồn tại** (`/api/v1/messages/...`), khiến mọi thao tác kiểm duyệt tin nhắn thất bại.

## Kafka

| Chiều | Topic | Event | Xử lý |
|---|---|---|---|
| Consume | `CONTENT_CREATED` | `ContentCreatedEvent` | Gọi ai-service `/moderate`; nếu độc hại → tạo report SYSTEM + log `AUTO_BAN` + publish BLOCK |
| Consume | `MESSAGE_CREATED` | `MessageCreatedEvent` | Kiểm duyệt nội dung tin nhắn chat |
| Publish | `MODERATION_ACTIONS` | `ModerationActionEvent` (BLOCK và UNBLOCK dùng **chung một class**) | media-service áp cho POST/COMMENT, chat-service áp cho MESSAGE |
| Publish | `USER_MODERATION_ACTIONS` | `UserModerationEvent` | auth-service đổi `AccountStatus`, thu hồi refresh token |

Bản cũ: `blockUser` publish `UserModerationEvent` lên chính topic `moderation-actions` mà **không ai consume**, nên khoá tài khoản là no-op hoàn toàn; BLOCK và UNBLOCK dùng hai class khác nhau; auto-ban ghi hai dòng log trong đó một dòng ghi sai là "Admin blocked content"; và `unblockContent` nhận `Long` nên không thể bỏ chặn tin nhắn (id là chuỗi ObjectId).

**AI không bao giờ fail-open**: lời gọi `/moderate` không được bọc try/catch; ai-service trả 502 khi không quét được media, exception lan ra và Kafka retry rồi đẩy vào DLT để người kiểm tra.

Auto-ban **idempotent**: nếu đã có report SYSTEM cho đối tượng đó thì event gửi lại không ghi gì thêm và không publish lại BLOCK.

## Permission

Các permission mà service này kiểm tra được auth-service `DatabaseSeeder` tạo sẵn và gán cho role ADMIN: `MODERATION:ACCESS`, `REPORT:VIEW_ALL`, `REPORT:PROCESS`, `COMPLAINT:VIEW_ALL`, `COMPLAINT:PROCESS`, `POST:DELETE_ANY`, `MESSAGE:READ_ANY`, `USER:BLOCK`, `USER:READ_SENSITIVE`.

## Test

`mvn -pl moderation-service test` → **28 test xanh** offline: `ReportServiceImplTest` 11, `ContentModerationListenerTest` 8, `ModerationControllerSecurityTest` 8, context load 1.

`ModerationControllerSecurityTest` khẳng định người dùng thường bị 403 ở các endpoint chặn, admin đi qua được.

## Lưu ý khi deploy

- Schema đổi: thêm cột `source`, `report_id`, `complaint_id`; `reporter_id` thành nullable; giá trị cột `action` chuyển từ chuỗi tự do sang tên enum. `ddl-auto: update` thêm cột nhưng **không backfill**; môi trường `prod` dùng `validate` nên cần migration thủ công.
- `ModerationMessageResponse` không còn `media` và `deletedAt` vì `MessageModerationView` của chat-service không cung cấp; `sentAt` đổi sang `Instant`.
- Thư viện `rsql-jpa 6.0.33` không tương thích Hibernate 7 trên H2, nên `application-test.yaml` loại trừ `RSQLJPAAutoConfiguration`. Môi trường thật dùng PostgreSQL nên không ảnh hưởng.
