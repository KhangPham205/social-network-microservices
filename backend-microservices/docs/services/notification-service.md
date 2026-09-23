# notification-service (port 8084)

Trung tâm thông báo hướng sự kiện. Nghe Kafka, dựng nội dung thông báo, lưu vào PostgreSQL riêng và đẩy realtime tới người nhận qua STOMP. Service này **không publish event nào**.

## Runtime

| | |
|---|---|
| Port | `${SERVER_PORT:8084}` |
| PostgreSQL | `notification_db` — `notifications`, `user_caches` |
| Kafka | chỉ consume, group `notification-service`, `ack-mode: record`, `enable-auto-commit: false` |
| WebSocket | endpoint `/ws/notification`, simple broker in-memory |
| Gọi ra ngoài | user-service `POST /api/v1/users/internal/summaries` khi thiếu actor trong cache |

## Domain model

| Bảng | Field chính |
|---|---|
| `notifications` | `id`, `event_id` (unique — khoá chống trùng), `receiver_id`, `actor` → `user_caches` (**LAZY**), `type` (`NotificationType`, STRING), `content`, `link`, `is_read`, `created_at`. Index `(receiver_id, created_at)` và `(receiver_id, is_read)` |
| `user_caches` | `id`, `display_name`, `avatar_url` — read model từ Kafka |

## REST API

| Method | Path | Mô tả |
|---|---|---|
| GET | `/api/v1/notifications?page=&size=` | Thông báo của người gọi, mới nhất trước, trả `PageVO<NotificationDto>` |
| GET | `/api/v1/notifications/unread-count` | Số thông báo chưa đọc |
| PUT | `/api/v1/notifications/{id}/read` | Đánh dấu đã đọc; không phải chủ sở hữu → **403**, không tồn tại → **404** |
| PUT | `/api/v1/notifications/read-all` | Đánh dấu tất cả đã đọc |

`NotificationDto` phơi ra thuộc tính JSON **`isRead`** (`@JsonProperty("isRead")`). Bản cũ để Lombok sinh `isRead()` trên field `isRead` nên Jackson đặt tên thuộc tính là `read`, khiến frontend đọc `isRead` luôn nhận `undefined` và coi mọi thông báo là chưa đọc. Có test khẳng định `$.content[0].isRead` tồn tại và `$.content[0].read` không tồn tại.

## Kafka

| Topic | Event | Xử lý |
|---|---|---|
| `NOTIFICATION` | `NotificationEvent` | Bỏ qua nếu actor = receiver; **chống trùng bằng `eventId`**; dựng `content`/`link` theo `NotificationType`; lưu; đẩy STOMP sau commit |
| `CHAT_NOTIFICATION` | `MessageNotificationEvent` | Tạo một thông báo `MESSAGE` cho **mỗi** người nhận (trừ người gửi), link `/chat/{roomId}` |
| `USER_CREATED` | `UserCreatedEvent` | Tạo `user_caches` |
| `PROFILE_UPDATED` | `ProfileUpdatedEvent` | Cập nhật displayName/avatarUrl |

Khi actor không có trong cache: gọi user-service `internal/summaries` rồi upsert. **Nếu lookup thất bại thì exception được ném ra** để Kafka retry rồi vào DLT. Bản cũ chỉ ghi log WARN và `return`, tức là thông báo biến mất vĩnh viễn.

Event thiếu `eventId`/`actorId`/`receiverId`/`type` → `IllegalArgumentException`, được cấu hình **không retry**, đi thẳng DLT.

Listener là adapter mỏng, toàn bộ logic dựng nội dung nằm một chỗ trong `NotificationService.render()`. Bản cũ có hai bản sao logic (một trong handler, một trong `NotificationServiceImpl.sendNotification` đã chết) và chúng đã phân kỳ: link `/user/` với `/users/`, văn bản khác nhau.

## WebSocket

- Endpoint duy nhất `/ws/notification` (bản cũ đăng ký thêm `/ws` không được gateway route).
- Origin giới hạn theo `${CORS_ALLOWED_ORIGINS}`, không còn `*`.
- JWT nhận từ cookie `jwt`, header `Authorization: Bearer` hoặc query `?token=` qua `JwtValidator.authenticate`.
- `SubscriptionAuthorizationInterceptor` chỉ cho phép SUBSCRIBE tới `/user/queue/**` của chính mình và `/topic/public`; chặn `/queue/...` thô, `/user/{id khác}/queue/...`, đuôi rỗng, destination null, session chưa xác thực.
- Đẩy tin qua `NotificationPusher`, luôn chạy ở `afterCommit` nên client không bao giờ nhận thông báo của một transaction bị rollback.

Destination: `/user/queue/notifications` (payload `NotificationDto`), `/user/queue/notification-summary` (`NotificationCountDto`), `/topic/public` (`PresenceEvent`).

## Test

`mvn -pl notification-service test` → **45 test xanh** offline.

| Lớp test | Số ca | Nội dung |
|---|---|---|
| `NotificationServiceImplTest` | 17 | Nội dung/link cho cả 7 loại, bỏ qua tự thông báo, chống trùng `eventId`, lỗi lookup actor được ném ra, event thiếu field → `IllegalArgumentException`, một thông báo MESSAGE mỗi người nhận, `markAsRead` kiểm tra sở hữu |
| `UserCacheServiceImplTest` | 9 | Cache hit không gọi user-service, cache miss gọi và upsert, id lạ → 404, lỗi transport được ném ra, xử lý idempotent |
| `SubscriptionAuthorizationInterceptorTest` | 11 | Các trường hợp cho phép và từ chối SUBSCRIBE |
| `NotificationControllerTest` | 7 | 401 khi ẩn danh, 403 khi đọc thông báo người khác, thuộc tính JSON là `isRead` |
| `NotificationServiceApplicationTests` | 1 | Context load với `@ActiveProfiles("test")` |

## Hạn chế đã biết

Broker STOMP là **simple broker in-memory**: nếu chạy nhiều instance, người nhận có thể đang kết nối ở instance khác với instance xử lý event và sẽ không nhận được push. Muốn scale cần broker relay (RabbitMQ) hoặc fan-out qua Redis pub/sub.
