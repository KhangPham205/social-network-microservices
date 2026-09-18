# 04 · Kafka & event contract

Mọi event là **Java record trong `com.socialnetwork.common.events`**, tên topic trong `KafkaTopics`. Producer gửi **object** (không tự serialize thành String) với key là id của aggregate; consumer khai báo tham số kiểu record. Serializer/deserializer cấu hình bằng yaml (Jackson 3, `JacksonJsonSerializer`/`JacksonJsonDeserializer`, trusted package `com.socialnetwork.common.events`, type header bật).

## Ma trận topic

| Topic (`KafkaTopics.*`) | Tên thật | Event | Producer | Consumer (group) | Ý nghĩa |
|---|---|---|---|---|---|
| `USER_CREATED` | `user-created-topic` | `UserCreatedEvent(accountId, username, email)` | auth | user (`user-service`: tạo profile), user (`user-service-neo4j`: node), media (`media-service`: user_caches), notification | Bắt đầu saga đăng ký |
| `PROFILE_CREATED` | `profile-created-topic` | `ProfileCreatedEvent(accountId)` | user | auth | Saga thành công → `PENDING` |
| `PROFILE_FAILED` | `profile-failed-topic` | `ProfileFailedEvent(accountId, reason)` | user | auth | Saga lỗi → `NOT_SOLVED` |
| `PROFILE_UPDATED` | `profile-updated-topic` | `ProfileUpdatedEvent(accountId, displayName, avatarUrl)` | user | media, notification | Cập nhật read-model `user_caches` |
| `FRIENDSHIP_EVENTS` | `friendship-events` | `FriendAcceptedEvent(senderId, receiverId)`, `FriendshipDeletedEvent(user1Id, user2Id)` | user | chat (`chat-service`), user (`user-service-neo4j`) | Tạo/đóng phòng chat riêng; cạnh `FRIENDS_WITH` |
| `NOTIFICATION` | `notification-topic` | `NotificationEvent(eventId, actorId, receiverId, type, targetId, postId)` | user, media | notification | `eventId` = khoá idempotent |
| `CHAT_NOTIFICATION` | `chat-notification-topic` | `MessageNotificationEvent(messageId, roomId, senderId, senderName, previewContent, recipientIds)` | chat | notification | Thông báo `MESSAGE` cho từng recipient |
| `CONTENT_CREATED` | `content-created-topic` | `ContentCreatedEvent(targetId, targetType, content, authorId, media)` | media | moderation (`moderation-service`), media (`media-service-recommendation`) | AI kiểm duyệt; index gợi ý |
| `MESSAGE_CREATED` | `message-created-topic` | `MessageCreatedEvent(messageId, roomId, senderId, content)` | chat | moderation | AI kiểm duyệt text chat |
| `MODERATION_ACTIONS` | `moderation-actions` | `ModerationActionEvent(targetId, targetType, action BLOCK/UNBLOCK, reason)` | moderation | media (POST/COMMENT), chat (MESSAGE) | Ẩn/khôi phục nội dung |
| `USER_MODERATION_ACTIONS` | `user-moderation-actions` | `UserModerationEvent(userId, newStatus, reason)` | moderation | auth | Khoá/mở khoá tài khoản |

Dead-letter: mỗi consumer có `DefaultErrorHandler` (3 lần retry, back-off 1s) + `DeadLetterPublishingRecoverer` → topic `<topic>.DLT`. `IllegalArgumentException` (event không hợp lệ) không retry.

## Cấu hình chuẩn (yaml, mọi service)

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JacksonJsonSerializer
      acks: all
    consumer:
      group-id: <service-name>
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JacksonJsonDeserializer
        spring.json.trusted.packages: com.socialnetwork.common.events
        spring.json.use.type.headers: true
    listener:
      ack-mode: record
```

Không còn `ProducerFactory`/`ConsumerFactory` tự viết. Bean duy nhất liên quan Kafka trong service: `CommonErrorHandler kafkaErrorHandler(...)`.

## Quy tắc

1. Publish **sau commit** (`TransactionSynchronization.afterCommit` / `@TransactionalEventListener(AFTER_COMMIT)` hoặc gọi ngoài `@Transactional`).
2. Consumer ghi DB phải **idempotent** (`eventId` unique, `existsById` trước insert).
3. Đổi contract → sửa record trong `common`, `mvn -pl common install`, compile toàn reactor; thêm field mới ở cuối và cho phép null để tương thích message cũ.
4. Không dùng `spring.json.type.mapping`; FQN trong type header đã trùng nhau vì mọi bên dùng class của `common`.
5. Không multiplex nhiều class không liên quan trên một topic (đã tách `USER_MODERATION_ACTIONS` khỏi `MODERATION_ACTIONS`).

## Các flow (sequence rút gọn)

**Đăng ký**: `auth.register` → commit → `USER_CREATED` → `user.UserSagaHandler` (skip nếu profile đã có) → `PROFILE_CREATED` + `PROFILE_UPDATED` → `auth.AuthSagaCompensator` `WAITING→PENDING`. Lỗi → `PROFILE_FAILED` → `NOT_SOLVED`.

**Kiểm duyệt bài**: `media.create` → `CONTENT_CREATED` → `moderation.ContentModerationListener` → `POST ai/moderate` → toxic: `Report(SYSTEM)` + `ModerationLog(AUTO_BAN)` + `MODERATION_ACTIONS(BLOCK)` → `media.ModerationActionEventListener` set `isSystemBan`. AI lỗi → exception → retry → DLT (fail closed).

**Thông báo**: producer `NotificationEvent.of(...)` → `notification.NotificationService` (dedupe `eventId`, actor từ `user_caches`, thiếu thì gọi user-service `internal/summaries`) → lưu → sau commit push `/user/{id}/queue/notifications` + `/queue/notification-summary`.
