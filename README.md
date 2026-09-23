# social-network-microservices

Backend mạng xã hội xây dựng theo kiến trúc microservices: đăng ký/đăng nhập, hồ sơ và quan hệ bạn bè, bài viết – bình luận – cảm xúc, chat thời gian thực, thông báo đẩy, kiểm duyệt nội dung bằng AI và gợi ý nội dung bằng vector search.

## Công nghệ

- **Java 21 · Spring Boot 4.0.3 · Spring Cloud 2025.1.0** (Gateway WebFlux, Eureka, LoadBalancer)
- **Kafka** cho giao tiếp bất đồng bộ, **Spring Kafka 4**
- **PostgreSQL** (mỗi service một database), **MongoDB** (tin nhắn), **Redis** (cache), **Neo4j** (đồ thị xã hội), **Milvus** (vector), **MinIO** (file)
- **Python 3.11 · FastAPI** cho AI service (PhoBERT SimCSE embedding, ViSoBERT hate-speech, ViT NSFW/vũ khí)
- **Docker Compose** cho toàn bộ hạ tầng và service

## Kiến trúc

```
Client → api-gateway (8080) → auth (8081) · user (8082) · media (8083)
                              notification (8084) · chat (8085) · moderation (8086)
                        ↕ Eureka (8761)          ↕ Kafka          → ai-service (8000)
```

Mỗi service sở hữu database riêng, tự xác thực JWT, và chỉ nói chuyện với service khác qua REST nội bộ (`/api/v1/<svc>/internal/**`, bảo vệ bằng token nội bộ) hoặc qua Kafka event dùng chung trong module `common`.

## Chạy nhanh

```bash
cd backend-microservices/docker
cp .env.example .env
docker compose up -d                 # hạ tầng: Postgres ×6, Kafka, Mongo, Redis, MinIO, Neo4j, Milvus, ai-service

cd ..
mvn -q -DskipTests install           # build toàn bộ module
mvn -pl discovery-server spring-boot:run   # rồi api-gateway, sau đó các service
```

Chạy trọn gói bằng Docker:

```bash
cd backend-microservices/docker
docker compose -f docker-compose.yml -f docker-compose.services.yml up --build
```

Swagger tổng hợp: <http://localhost:8080/swagger-ui.html> · Eureka: <http://localhost:8761>

## Tài liệu

Toàn bộ tài liệu kiến trúc nằm trong [`backend-microservices/docs/`](backend-microservices/docs/):

| Tài liệu | Nội dung |
|---|---|
| [01 · Kiến trúc tổng quan](backend-microservices/docs/01-kien-truc-tong-quan.md) | Sơ đồ hệ thống, danh sách service, luồng nghiệp vụ chính |
| [02 · Module common](backend-microservices/docs/02-common-module.md) | Thư viện dùng chung: contract, exception, security, auto-configuration |
| [03 · Bảo mật](backend-microservices/docs/03-bao-mat.md) | JWT, phân quyền, internal API, WebSocket |
| [04 · Kafka & event](backend-microservices/docs/04-kafka-events.md) | Ma trận topic, hợp đồng event, quy tắc producer/consumer |
| [05 · Cấu hình & triển khai](backend-microservices/docs/05-cau-hinh-va-trien-khai.md) | Biến môi trường, profile, chạy local và Docker |
| [06 · Quy ước code](backend-microservices/docs/06-quy-uoc-code.md) | Phân lớp, quy tắc bắt buộc, checklist trước khi merge |
| [services/](backend-microservices/docs/services/) | Chi tiết từng service: API, domain, event, cấu hình |

Quy ước dành cho contributor và AI assistant: [`backend-microservices/CLAUDE.md`](backend-microservices/CLAUDE.md).

## Giấy phép

Xem [LICENSE](LICENSE).
