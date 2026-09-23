# user-service (port 8082)

Sở hữu phần "xã hội" của người dùng: hồ sơ, theo dõi, kết bạn/chặn, gợi ý bạn bè bằng Neo4j, màn hình quản trị người dùng, và nửa còn lại của saga đăng ký.

## Runtime

| | |
|---|---|
| Port | `${SERVER_PORT:8082}` |
| PostgreSQL | `user_db` — `users`, `user_info`, `friendship`, `user_rela` |
| Neo4j | node `UserNode`, cạnh `FRIENDS_WITH` (dùng chung instance với media-service) |
| Kafka | group `user-service` (saga) và `user-service-neo4j` (đồng bộ đồ thị) |
| Gọi ra ngoài | auth-service `/api/v1/auth/internal/credentials/batch` và `/credentials/{id}` |

## Domain model

| Bảng | Field chính |
|---|---|
| `users` | `id` (= accountId của auth, gán sẵn không sinh tự động), `display_name`, `avatar_url`, `last_active_at`, `user_info` (OneToOne cascade) |
| `user_info` | `id` dùng chung khoá chính qua `@MapsId`, `bio`, `favorites`, `date_of_birth` |
| `friendship` | `sender` (`user_id`), `receiver` (`friend_id`), `status` (`FriendshipStatus`, STRING, NOT NULL), **unique(`user_id`, `friend_id`)**, kế thừa `BaseEntity` |
| `user_rela` | `follower`, `following` |

`friendship` lưu một dòng cho mỗi cặp, hướng thể hiện ai là người khởi tạo. Dòng `BLOCKED` có hướng: `sender` là người chặn.

## REST API

| Method | Path | Mô tả |
|---|---|---|
| GET | `/api/v1/users/me` · `/{userId}` | Hồ sơ; trả **404** nếu một trong hai phía đã chặn nhau |
| PUT | `/api/v1/users/me` | Cập nhật hồ sơ, phát `ProfileUpdatedEvent` |
| GET | `/api/v1/users/search?filter=&page=&size=` | RSQL hoặc từ khoá; **loại bỏ người đã chặn hai chiều** |
| POST · DELETE | `/api/v1/users/follow` · `/unfollow` | Theo dõi; bị từ chối nếu có quan hệ chặn |
| GET | `/api/v1/users/{id}/followers` · `/following` · `/relation-status` | |
| GET | `/api/v1/users/{userId}/recommendations` | Bạn của bạn từ Neo4j |
| POST | `/api/v1/users/friendship/send` · `/unsend` | Gửi / thu hồi lời mời |
| POST | `/api/v1/users/friendship/accept` · `/reject` | **Chỉ người nhận** mới thực hiện được |
| DELETE | `/api/v1/users/friendship/unfriend` | Chỉ xoá dòng `FRIEND`; không đụng dòng `BLOCKED` |
| POST · DELETE | `/api/v1/users/friendship/block` · `/unblock` | Chặn có hướng; `unblock` chỉ xoá dòng của chính người gọi |
| GET | `/api/v1/users/friendship[/{userId}|/sent|/pending|/blocked]` | Danh sách quan hệ |

### Admin (`/api/v1/users/admin/**`, `hasRole("ADMIN")`)

| Method | Path | Permission |
|---|---|---|
| GET | `/api/v1/users/admin` · `/{userId}` | `USER:READ_ALL` |
| PUT | `/api/v1/users/admin/{userId}` | `USER:UPDATE_ANY` |
| POST | `/api/v1/users/admin/neo4j/sync` | chỉ ADMIN (trước đây nằm ở `/api/v1/users/neo4j/sync` và **permitAll**, ai cũng kích hoạt được migration toàn bộ) |

### Internal (`X-Internal-Token`)

| Method | Path | Request → Response |
|---|---|---|
| POST | `/api/v1/users/internal/create?accountId=&displayName=` | → **201** `UserSummary` |
| GET | `/api/v1/users/internal/{userId}` | → `UserProfileDto` |
| POST | `/api/v1/users/internal/summaries` | `List<Long>` → `List<UserSummary>` **(mới)** |
| GET | `/api/v1/users/internal/{userId}/network-ids` | → `List<Long>` |
| GET | `/api/v1/users/internal/check-friendship?user1=&user2=` | → `boolean` |
| GET | `/api/v1/users/internal/{id}/admin-detail` | → `UserModerationDto` |
| POST | `/api/v1/users/internal/batch` | `List<Long>` → `List<UserModerationDto>` |

Endpoint `summaries` là cái mà chat-, notification- và moderation-service dùng để tra tên/avatar hàng loạt thay vì gọi từng người.

## Các lỗi nghiệp vụ đã sửa

| Lỗi | Hậu quả trước đây |
|---|---|
| Ai cũng accept được lời mời | Người **gửi** tự chấp nhận lời mời của chính mình |
| `unfriend` xoá mọi dòng giữa cặp | Huỷ kết bạn cũng xoá luôn dòng `BLOCKED` → **bỏ chặn ngoài ý muốn** |
| `block` xoá dòng của đối phương | Người bị chặn có thể chặn ngược rồi bỏ chặn để gỡ chặn của người kia |
| `block` không phát event | chat-service không biết để lưu trữ phòng |
| `FriendshipResponse.from` | Trả `senderId == receiverId == người xem` khi người xem là người nhận |
| `findBlockedUserIds` trả `int` | `BlockUtils` không dùng được, **lọc chặn bị comment out hoàn toàn** |
| N+1 khi liệt kê bạn bè | Mỗi dòng friendship sinh 2 query phụ |

Nay `block`/`unfriend` đúng ngữ nghĩa, lọc chặn được áp dụng thật trong tìm kiếm và xem hồ sơ, và các truy vấn danh sách dùng `@EntityGraph`/`JOIN FETCH`.

## Kafka

| Chiều | Topic | Event |
|---|---|---|
| Consume (`user-service`) | `USER_CREATED` | `UserSagaHandler` tạo hồ sơ — **idempotent**: hồ sơ đã tồn tại thì chỉ phát lại `ProfileCreatedEvent` |
| Publish | `PROFILE_CREATED` / `PROFILE_FAILED` | Phản hồi saga cho auth-service |
| Publish | `PROFILE_UPDATED` | Khi tạo hồ sơ, khi người dùng sửa hồ sơ, khi admin sửa |
| Publish | `NOTIFICATION` | `FRIEND_REQUEST`, `FRIEND_ACCEPT` |
| Publish | `FRIENDSHIP_EVENTS` | `FriendAcceptedEvent`; `FriendshipDeletedEvent` khi huỷ kết bạn **và khi chặn** |
| Consume (`user-service-neo4j`) | `USER_CREATED`, `FRIENDSHIP_EVENTS` | Đồng bộ node và cạnh `FRIENDS_WITH` |

Mọi lần publish đều chạy sau commit. Listener không nuốt exception nên retry/DLT hoạt động.

**Lỗi hạ tầng đã sửa**: module khai báo `org.springframework.kafka:spring-kafka` thô. Trên Boot 4, auto-configuration Kafka nằm ở artifact riêng do `spring-boot-starter-kafka` kéo vào, nên **mọi `@KafkaListener` của service này chưa bao giờ chạy** và `EventPublisher` không tạo được bean.

## Test

`mvn -pl user-service test` → **20 test xanh** offline.

- `FriendshipServiceImplTest` (8): accept bởi người nhận, từ chối accept/reject bởi người gửi, chặn giữ nguyên dòng BLOCKED của đối phương và xoá follow hai chiều, unfriend chỉ đụng dòng FRIEND, unblock chỉ xoá dòng người gọi.
- `UserSagaHandlerTest` (3): lần đầu, lần gửi lại (không lỗi, phát lại `ProfileCreatedEvent`), thất bại → `ProfileFailedEvent`.
- `UserServiceImplTest` (2), `AdminUserControllerTest` (6), context load (1).

## Việc còn tồn đọng

- `GET /friendship/{userId}` tính cờ quan hệ theo `userId` chứ không theo người gọi. Chưa sửa vì sẽ đổi nội dung response mà frontend đang dùng.
- `unfriend`/`unblock` vẫn trả `FriendshipStatus.REJECTED` thay vì `NONE`; giữ nguyên vì lý do tương tự.
- `user_rela` chưa có unique(`follower`, `following`); thêm ràng buộc trên dữ liệu đang chạy sẽ fail nếu đã có bản ghi trùng.
- Thư viện `rsql-jpa 6.0.33` không tương thích Hibernate 7 khi chạy H2: `RSQLJPAAutoConfiguration` dò `DerbyDialect` vốn đã bị Hibernate 7 loại bỏ. Test của module vá bằng cách ghim `spring.jpa.database-platform: org.hibernate.dialect.PostgreSQLDialect`. Môi trường thật không bị vì `PostgreSQLDialect` được kiểm tra trước.
