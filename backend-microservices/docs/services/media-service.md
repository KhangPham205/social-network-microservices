# media-service (port 8083)

Service nội dung: bài viết, chia sẻ, bình luận (1 cấp trả lời), cảm xúc có phân loại, lưu file lên MinIO, và feed khám phá lai giữa vector search (Milvus) và điểm xã hội (Neo4j).

Đây là service phụ thuộc nhiều hệ thống ngoài nhất, nên phần lớn công sức refactor nằm ở chỗ **không chết khi một phụ thuộc chết**.

## Runtime

| | |
|---|---|
| Port | `${SERVER_PORT:8083}` |
| PostgreSQL | `media_db` — `posts`, `comments`, `reacts`, `react_types`, `user_caches` |
| Redis | cache (`spring.cache.type`) |
| Neo4j | node `Post`, cạnh `POSTED` (dùng chung instance với user-service) |
| MinIO | bucket `${MINIO_BUCKET:social-media}` |
| Milvus | collection `posts`, vector 768 chiều |
| ai-service | `${AI_SERVICE_URL}` cho embedding |
| Gọi ra ngoài | user-service `/api/v1/users/internal/{id}/network-ids`, `/internal/check-friendship` |

## Domain model

| Bảng | Field chính |
|---|---|
| `posts` | `id`, `content`, `media` (JSON `[{type,url}]`), `access_modifier` (`AccessScope` PUBLIC/FRIENDS/PRIVATE, STRING), `author` → `user_caches` (EAGER), `shared_post` **ManyToOne** (một bài được chia sẻ nhiều lần), `react_count`/`comment_count`/`share_count`, `is_system_ban` (mặc định false), `deleted_at` (soft delete), `created_at`/`updated_at` |
| `comments` | `id`, `author`, `post`, `parent` (1 cấp), `content`, `media`, `react_count`, `deleted_at`, `is_system_ban` |
| `reacts` | unique(`user_id`, `target_id`, `target_type`), `target_type` = `TargetType`, `react_type` → `react_types` |
| `react_types` | `id`, `name` unique — được `ReactTypeSeeder` nạp sẵn LIKE, LOVE, HAHA, WOW, SAD, ANGRY |
| `user_caches` | `id` (= accountId), `display_name`, `avatar_url` — read model, cập nhật từ Kafka |

## REST API

Tất cả dưới `/api/v1/media`, yêu cầu đăng nhập trừ khi ghi chú khác.

| Method | Path | Mô tả |
|---|---|---|
| GET | `/posts/feed` | Feed khám phá: embedding → Milvus → điểm Neo4j → lọc theo network và access scope. **Suy giảm mượt**: lỗi AI/Milvus/Neo4j thì trả feed theo thời gian |
| GET | `/posts/{postId}` | Chi tiết bài viết (kiểm tra quyền xem) |
| POST | `/posts/create` | multipart: `content`, `accessModifier`, `media[]` |
| PUT | `/posts/update` | multipart; `removeMediaUrls` chỉ xoá được file thuộc chính bài đó |
| POST | `/posts/share` | Chia sẻ, chuỗi chia sẻ được làm phẳng về bài gốc |
| GET | `/posts/me` · `/posts/user/{userId}` | Bài của mình / của người khác (lọc theo quan hệ bạn bè và scope) |
| DELETE | `/posts/{postId}` | **Soft delete** (đặt `deleted_at`), không còn xoá cứng gây lỗi khoá ngoại |
| GET | `/comments/post/{postId}` · `/comments/{commentId}/replies` | Danh sách bình luận / trả lời |
| POST · PUT · DELETE | `/comments/create` · `/comments/update` · `/comments/{commentId}` | Tạo / sửa / soft delete |
| POST | `/reacts/toggle` | Thêm / đổi / gỡ cảm xúc |
| GET | `/reacts/{targetType}/{targetId}/users` · `/summary` | Có kiểm tra quyền xem đối tượng |
| GET | `/recommendations/explore` | Lấy userId từ token, **không** còn nhận qua query param |
| POST | `/admin/recommendations/sync-posts` | `hasRole('ADMIN')` — index lại toàn bộ bài |

### Internal (`X-Internal-Token`)

| Method | Path | Response |
|---|---|---|
| GET | `/api/v1/media/internal/posts/{id}/owner-id` | `Long` |
| POST | `/api/v1/media/internal/posts/batch` | `List<PostResponse>` |
| GET | `/api/v1/media/internal/comments/{id}/owner-id` | `Long` |
| POST | `/api/v1/media/internal/comments/batch` | `List<CommentResponse>` |

## Kiểm soát truy cập

`ContentAccessService` là nơi duy nhất quyết định "ai được xem gì": tác giả và ADMIN xem được tất cả; bài đã xoá mềm hoặc bị ban hệ thống trả 404 cho người khác; PRIVATE trả 403; FRIENDS gọi user-service kiểm tra quan hệ bạn bè. `getReactUsers`, `getReactSummary` và `toggleReact` đều đi qua nó.

Hai lỗi thật đã sửa:

- Specification của feed cũ ghép điều kiện bằng **OR** (`chưa bị ban` HOẶC `chưa xoá` HOẶC `là bài của tôi`), nên bài bị ban mà chưa xoá vẫn lọt. Nay ghép bằng AND.
- `@Cacheable("post_details")` cache theo `postId` nhưng nội dung phụ thuộc người xem, và cache hit bỏ qua kiểm tra quyền. Đã bỏ hẳn.

## Kafka

| Chiều | Topic | Event |
|---|---|---|
| Publish | `CONTENT_CREATED` | `ContentCreatedEvent` cho POST và COMMENT, key = id, gửi **sau commit** qua `KafkaEventPublisher` |
| Publish | `NOTIFICATION` | `NotificationEvent.of(...)` khi có bình luận / trả lời / cảm xúc |
| Consume | `USER_CREATED`, `PROFILE_UPDATED` | `UserCacheEventListener` tạo và cập nhật `user_caches` |
| Consume | `MODERATION_ACTIONS` | BLOCK/UNBLOCK cho POST và COMMENT |
| Consume | `CONTENT_CREATED` (group `media-service-recommendation`) | `ContentIndexingListener` index bài lên Milvus + Neo4j, bỏ qua bài PRIVATE và đã xoá |

**Bug nặng nhất đã sửa**: bản cũ publish event bằng cách tự `writeValueAsString` rồi để serializer bọc thêm một lớp (double-encode), còn consumer `UserCacheSyncHandler` thì `convertValue` một String sang record 3 tham số nên **luôn ném lỗi**. Hệ quả: `user_caches` không bao giờ có dữ liệu, và mọi lần tạo post/comment đều fail với "User cache not found". Nay producer gửi object, consumer nhận tham số có kiểu.

## Chịu lỗi khi phụ thuộc chết

| Phụ thuộc | Trước | Sau |
|---|---|---|
| Milvus | `MilvusServiceClient` kết nối ngay lúc tạo bean → **service không khởi động được** nếu Milvus chưa chạy | Bean `@Lazy` + khởi tạo collection ở `ApplicationReadyEvent`, log rồi tiếp tục; `insertPost` no-op, `searchSimilarPosts` trả rỗng |
| ai-service | URL `http://localhost:8000` hardcode, không timeout | `${AI_SERVICE_URL}` + connect 2s / read 10s, lỗi trả embedding rỗng |
| MinIO | tạo bucket trong `@PostConstruct` | `ApplicationReadyEvent`, log rồi tiếp tục |
| user-service | lỗi là 500 | feed thu hẹp về bài của chính mình |
| Neo4j | lỗi là 500 | chỉ bỏ thành phần điểm xã hội trong xếp hạng |

Ngoài ra `MinioStorageServiceImpl` từng đọc `@Value("${app.minio.public-url}")` — một key **không tồn tại trong yaml nào**, nghĩa là bean không khởi tạo được. Nay đọc qua `MinioProperties`.

## Test

`mvn -pl media-service test` → **17 test xanh** offline.

- `MediaServiceApplicationTests`: `@ActiveProfiles("test")` + mock `MilvusServiceClient`, Neo4j `Driver`, `MinioClient`.
- `ModerationActionEventListenerIntegrationTest`: `@EmbeddedKafka` + H2, chờ partition assignment bằng `ContainerTestUtils` thay vì `Thread.sleep`, publish event có kiểu.
- `ContentAccessServiceImplTest` (8 ca): PUBLIC, FRIENDS có/không phải bạn, PRIVATE, bài bị ban, bài xoá mềm, admin, bài không tồn tại.
- `ModerationActionEventListenerTest` (5 ca).
