# chat-service (port 8085)

Chat 1-1 và nhóm. Phòng chat và thành viên nằm ở PostgreSQL, tin nhắn nằm ở MongoDB, đẩy realtime qua STOMP. Tự tạo phòng riêng khi hai người thành bạn.

> ⚠️ **Refactor này thay đổi hợp đồng API với frontend.** Xem mục [Thay đổi phá vỡ tương thích](#thay-đổi-phá-vỡ-tương-thích) ở cuối trang trước khi deploy.

## Runtime

| | |
|---|---|
| Port | `${SERVER_PORT:8085}` |
| PostgreSQL | `chat_db` — `chat_rooms`, `room_members`, `room_member_labels` |
| MongoDB | `${MONGODB_URI}`, collection `messages` |
| Redis | Spring Cache `user_summaries`, TTL 5 phút |
| Kafka | group `chat-service` |
| WebSocket | `/ws/chat`, simple broker in-memory |
| Gọi ra ngoài | user-service `POST /api/v1/users/internal/summaries` |

## Domain model

| Bảng / collection | Field chính |
|---|---|
| `chat_rooms` | `id`, `is_group` (NOT NULL), `title`, `media_url`, **`pair_key`** (`min:max` của 2 userId, unique — đảm bảo mỗi cặp chỉ một phòng riêng), **`active`** (phòng bị lưu trữ khi huỷ kết bạn), `created_at`/`updated_at` |
| `room_members` | `@EmbeddedId(roomId, userId)`, `role` (`ConversationRole` OWNER/ADMIN/MEMBER), `joined_at`, `labels` (`ChatLabel`, ElementCollection) |
| `messages` (Mongo) | `id` (ObjectId), `roomId`, `senderId`, `senderName`, `senderAvatar`, `replyToId` (**String**), `content`, `type` (`MessageType`), `media` (`List<MediaItem>`), `createdAt`, `readBy`, `isDeleted`, `deletedAt`, `isSystemBan` |

## REST API

| Method | Path | Quyền |
|---|---|---|
| POST | `/api/v1/chat/conversations/create` | đăng nhập; nhóm cần title và ≥1 thành viên, phòng riêng cần **đúng 1** thành viên khác |
| PUT | `/api/v1/chat/conversations/update` | OWNER/ADMIN |
| POST | `/api/v1/chat/conversations/addMembers` | OWNER/ADMIN |
| PUT | `/api/v1/chat/conversations/updateRoleMember` | OWNER/ADMIN |
| DELETE | `/api/v1/chat/conversations/{id}/members/{userId}` | OWNER/ADMIN (ADMIN không xoá được ADMIN/OWNER) |
| DELETE | `/api/v1/chat/conversations/{id}/leave` | thành viên |
| GET | `/api/v1/chat/conversations/me` · `/{id}` | thành viên |
| POST · DELETE | `/api/v1/chat/conversations/{roomId}/labels` | thành viên; trả 204 |
| GET | `/api/v1/chat/conversations/labels/all` | đăng nhập |
| POST | `/api/v1/chat/messages` | **thành viên phòng** (JSON body) |
| GET | `/api/v1/chat/messages/{conversationId}/cursor?before=&limit=` | thành viên; `limit` giới hạn 1..100 |
| DELETE | `/api/v1/chat/messages/{messageId}` | chỉ tác giả (soft delete + broadcast thu hồi) |

### Internal (`X-Internal-Token`)

| Method | Path | Response |
|---|---|---|
| GET | `/api/v1/chat/internal/messages/{id}` | `MessageModerationView` |
| GET | `/api/v1/chat/internal/messages/{id}/owner-id` | `Long` |
| POST | `/api/v1/chat/internal/messages/batch` | `List<String>` → `List<MessageModerationView>` |

## Phân quyền (các lỗi IDOR đã sửa)

Bản cũ cho phép bất kỳ ai đăng nhập cũng: gửi tin vào **phòng bất kỳ**, đọc **toàn bộ lịch sử** phòng bất kỳ qua `/messages/{id}/all`, và SUBSCRIBE vào topic của phòng bất kỳ. Nay:

- `createAndSaveMessage` kiểm tra membership cho cả đường REST lẫn STOMP.
- `/messages/{id}/all` đã bị xoá; dùng endpoint cursor có kiểm tra membership.
- `markMessageAsRead` kiểm tra membership **và** tin nhắn đúng thuộc phòng đó.
- `SubscriptionAuthorizationInterceptor` chỉ cho SUBSCRIBE `/queue/conversation/{id}` và `/topic/conversation/{id}` khi là thành viên; mọi trường hợp khác bị từ chối.
- Handshake WebSocket giới hạn origin theo `${CORS_ALLOWED_ORIGINS}` (bản cũ để `*`, cộng với cookie `SameSite=None` là lỗ hổng cross-site WebSocket hijacking).

## WebSocket

- Endpoint `/ws/chat`; JWT lấy từ header `Authorization: Bearer`, cookie `jwt` hoặc `?token=`.
- Inbound: `/app/chat.send`, `/app/chat.read` (hằng số trong `WebSocketConstants`, không còn lặp tiền tố `/app` như bản cũ khiến handler không bao giờ khớp).
- Outbound: `/queue/conversation/{roomId}` nhận `MessageResponse`; `/topic/conversation/{roomId}` nhận `RoomEvent` (`MESSAGE_REVOKED`, `MESSAGE_READ`, `CONVERSATION_DELETED`, `CONVERSATION_ARCHIVED`).
- Mọi lần đẩy đều chạy sau khi transaction commit.

## Kafka

| Chiều | Topic | Event | Xử lý |
|---|---|---|---|
| Consume | `FRIENDSHIP_EVENTS` | `FriendAcceptedEvent` | Tạo phòng riêng (idempotent, mở lại phòng đã lưu trữ) |
| Consume | `FRIENDSHIP_EVENTS` | `FriendshipDeletedEvent` | Lưu trữ phòng + broadcast `CONVERSATION_ARCHIVED`. Bản cũ chỉ ghi log |
| Consume | `MODERATION_ACTIONS` | `ModerationActionEvent` (`TargetType.MESSAGE`) | Đặt/gỡ `isSystemBan`, broadcast `MESSAGE_REVOKED`. Bản cũ không hề consume, nên lệnh chặn tin nhắn là no-op |
| Publish | `MESSAGE_CREATED` | `MessageCreatedEvent` | moderation-service quét nội dung |
| Publish | `CHAT_NOTIFICATION` | `MessageNotificationEvent` | notification-service tạo thông báo cho người nhận |

Listener không nuốt exception nữa; lỗi đi qua retry rồi vào DLT.

## Hiệu năng

`getUserConversations` trước đây gọi user-service **một lần cho mỗi thành viên của mỗi phòng** (50 phòng × 5 người = 250 HTTP request cho một lần mở danh sách chat). Nay `CachedUserDirectory` đọc cache Redis trước rồi gom toàn bộ id còn thiếu vào **một** lời gọi `internal/summaries`; truy vấn thành viên cũng gộp thành một `findByIdRoomIdIn`.

## Test

`mvn -pl chat-service test` → **40 test xanh** offline: `ConversationServiceImplTest` 19, `MessageServiceImplTest` 10, `SubscriptionAuthorizationInterceptorTest` 6, `CachedUserDirectoryTest` 4, context load 1.

Lưu ý khi viết test: chỉ mock `ChatMessageRepository`, **đừng** mock cả `MongoTemplate` vì sẽ làm hỏng `gridFsTemplate` của Boot (`MongoConverter must not be null`).

## Thay đổi phá vỡ tương thích

Cần cập nhật frontend và, với `prod`, cần migration schema.

**API**

| Trước | Sau |
|---|---|
| `POST /messages` và 2 endpoint ghi của `/conversations` nhận `multipart/form-data` | Nhận JSON `@RequestBody`. Trường `mediaFiles` (`List<MultipartFile>`) cũ **bị bỏ qua âm thầm**, nay thay bằng `media: [{url, type, name}]` và được lưu thật |
| Payload tin nhắn là `Map<String,Object>` với khoá `roomId` | `MessageResponse` với khoá `conversationId`; bỏ `reactions` (chưa bao giờ có dữ liệu) |
| `GET /messages/{id}/all` | Đã xoá, dùng `/{id}/cursor` |
| Endpoint label trả chuỗi tiếng Việt | Trả 204 |
| Push `EVENT_READ`, `EVENT_CONVERSATION_DELETED` | `RoomEvent` với `MESSAGE_READ`, `CONVERSATION_DELETED` |
| `isGroup` lúc là `isGroup` lúc là `group` tuỳ endpoint | Luôn là `isGroup` |

**Schema** (prod dùng `ddl-auto: validate` nên phải chạy trước): thêm `chat_rooms.pair_key` + unique index, thêm `chat_rooms.active NOT NULL DEFAULT true`, `is_group` thành NOT NULL. Phòng riêng cũ cần backfill `pair_key` từ hai thành viên, và **phải gộp các phòng riêng trùng cặp trước** nếu không unique index sẽ tạo thất bại.

**Kafka**: consumer group đổi `chat-service-group-final` → `chat-service` với `auto-offset-reset: earliest`, nên lần khởi động đầu sẽ đọc lại lịch sử `friendship-events` và `moderation-actions`. Việc tạo phòng và đặt ban đều idempotent nên an toàn.
