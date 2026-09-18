# 05 · Cấu hình, chạy local & triển khai

## Hồ sơ cấu hình (profile)

Mỗi service Java có đúng **2 file**:

- `application.yaml`: toàn bộ key, giá trị theo môi trường viết dạng `${ENV_VAR:default}`; default trỏ về hạ tầng trong `docker/docker-compose.yml` (localhost). **Không** có `spring.profiles.active`.
- `application-prod.yaml`: override cho prod (`ddl-auto: validate`, `show-sql: false`, secret **không có default**). Kích hoạt bằng `SPRING_PROFILES_ACTIVE=prod`.
- Test: `src/test/resources/application-test.yaml` (H2, Eureka off, Kafka listener `auto-startup: false`) + `@ActiveProfiles("test")`.

## Biến môi trường chuẩn

| Biến | Dùng bởi | Default local |
|---|---|---|
| `SERVER_PORT` | tất cả | port riêng từng service |
| `EUREKA_URL` | tất cả trừ discovery | `http://localhost:8761/eureka/` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | auth, user, media, notification, chat, moderation | `jdbc:postgresql://localhost:543x/<db>`, `root`, `password` |
| `KAFKA_BOOTSTRAP_SERVERS` | auth, user, media, notification, chat, moderation | `localhost:9092` |
| `JWT_SECRET` | 6 business service | `dev-only-jwt-secret-change-me-0123456789abcdef` |
| `INTERNAL_API_TOKEN` | 6 business service | `dev-only-internal-token-change-me` |
| `REDIS_HOST`, `REDIS_PORT` | media, chat | `localhost`, `6379` |
| `MONGODB_URI` | chat | `mongodb://admin:password@localhost:27017/chat_mongo_db?authSource=admin` |
| `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD` | user, media | `bolt://localhost:7687`, `neo4j`, `password` |
| `MINIO_URL`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`, `MINIO_PUBLIC_URL` | media | `http://localhost:9000`, `admin`, `password123`, `social-media`, `http://localhost:9000` |
| `MILVUS_URI` | media | `http://localhost:19530` |
| `AI_SERVICE_URL` | media, moderation | `http://localhost:8000` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | auth | `smtp.gmail.com`, `587`, **không default** |
| `CORS_ALLOWED_ORIGINS` | gateway, chat, notification | `http://localhost:3000,http://localhost:5173` |
| `EUREKA_HOSTNAME` | discovery | `localhost` |

Port host của Postgres theo compose: auth 5432, user 5433, media 5434, notification 5435, chat 5436, moderation 5437.

## Chạy local (dev)

```bash
# 1. Hạ tầng (Postgres x6, Mongo, Redis, MinIO, Kafka, Neo4j, Milvus, ai-service + UI quản trị)
cd backend-microservices/docker
cp .env.example .env            # chỉnh nếu cần
docker compose up -d

# 2. Build toàn bộ (common được cài vào ~/.m2 trước)
cd ..
mvn -q -DskipTests install

# 3. Chạy theo thứ tự: discovery -> gateway -> các service (mỗi lệnh một terminal)
mvn -pl discovery-server spring-boot:run
mvn -pl api-gateway spring-boot:run
mvn -pl auth-service spring-boot:run   # tương tự user-service, media-service, ...
```

Swagger gom tại `http://localhost:8080/swagger-ui.html`; Eureka dashboard `http://localhost:8761`; Kafka UI `:8888`; MinIO console `:9001`; pgAdmin `:5050`; Mongo Express `:8090`; Attu (Milvus) `:8001`; Neo4j Browser `:7474`.

## Chạy toàn bộ bằng Docker

```bash
cd backend-microservices/docker
docker compose -f docker-compose.yml -f docker-compose.services.yml up --build
```

`docker-compose.services.yml` build 8 module Java từ `backend-microservices/Dockerfile` (multi-stage, `ARG MODULE`, Java 21, non-root, healthcheck `/actuator/health`) và cấp env trỏ tới hostname trong compose network (`kafka:9094`, `postgres-auth:5432`, `discovery-server:8761`, `ai-service:5000`, …). Chỉ publish port 8080 (gateway) và 8761 (Eureka).

Build một image lẻ:

```bash
docker build -f backend-microservices/Dockerfile --build-arg MODULE=user-service -t sn/user-service backend-microservices
```

## Build & test

```bash
mvn -q -DskipTests install           # build tất cả
mvn -pl <module> test                # test một module (offline được: thêm -o)
mvn -pl common install               # bắt buộc sau khi đổi common
```

Test không cần hạ tầng: dùng H2 + `@MockitoBean` cho client ngoài (Milvus, Neo4j driver, MinIO, Mongo). Test tích hợp Kafka dùng `@EmbeddedKafka`.

## Prod checklist

- Set đầy đủ env không có default: `JWT_SECRET` (≥32 byte, random), `INTERNAL_API_TOKEN`, `DB_PASSWORD`, `MAIL_USERNAME/MAIL_PASSWORD`, `MINIO_*`, `NEO4J_PASSWORD`, `MONGODB_URI`.
- `SPRING_PROFILES_ACTIVE=prod`; schema phải tồn tại trước (`ddl-auto: validate`).
- `CORS_ALLOWED_ORIGINS` = domain frontend thật; `MINIO_PUBLIC_URL` = URL public của bucket.
- Đặt Eureka, Kafka, ai-service, các DB trong mạng nội bộ; chỉ gateway public.
