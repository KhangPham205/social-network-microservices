# api-gateway & discovery-server

Hai module ở "vành ngoài" hệ thống. Không module nào phụ thuộc `common`, không có database, không có Kafka.

---

## api-gateway (port 8080)

Cửa vào duy nhất từ Internet. Là **reverse proxy thuần**: route theo prefix, terminate CORS, chặn đường dẫn nội bộ, gom tài liệu OpenAPI. Gateway **không** xác thực JWT — mỗi service tự làm việc đó (xem `docs/03-bao-mat.md`).

### Runtime

| | |
|---|---|
| Stack | Spring Cloud Gateway 5 (WebFlux / Reactor Netty), Spring Boot 4.0.3 |
| Main class | `com.socialnetwork.api_gateway.ApiGatewayApplication` |
| Port | `${SERVER_PORT:8080}` |
| Service discovery | Eureka client, `${EUREKA_URL:http://localhost:8761/eureka/}`, `prefer-ip-address: true`, **không tự đăng ký** (`register-with-eureka: false`) vì không ai cần khám phá gateway |
| Timeout | `spring.cloud.gateway.server.webflux.httpclient.connect-timeout: 2000`, `response-timeout: 15s` |
| Actuator | `health`, `info`, `gateway` (không được route ra ngoài) |

### Bảng route

Mỗi service có 2 route: REST và OpenAPI. Sinh tự động bởi helper `service(...)` trong `GatewayRoutesConfig`.

| Route id | Predicate | Target |
|---|---|---|
| `auth-service` | `/api/v1/auth/**` | `lb://auth-service` |
| `user-service` | `/api/v1/users/**` | `lb://user-service` |
| `media-service` | `/api/v1/media/**` | `lb://media-service` |
| `notification-service` | `/api/v1/notifications/**` | `lb://notification-service` |
| `chat-service` | `/api/v1/chat/**` | `lb://chat-service` |
| `moderation-service` | `/api/v1/moderation/**` | `lb://moderation-service` |
| `<svc>-swagger` | `/aggregate/<svc>/v3/api-docs/**` | `lb://<svc>`, rewrite bỏ tiền tố `/aggregate/<svc>` (giữ nguyên sub-path như `/swagger-config`) |
| `notification-ws` | `/ws/notification/**` | `lb:ws://notification-service` |
| `chat-service-ws` | `/ws/chat/**` | `lb:ws://chat-service` |

Đã xoá các route chết của bản cũ: `/api/v1/reports/**`, `/api/v1/complaints/**` (moderation không phục vụ 2 prefix này) và route `chat-service-swagger-ui` bị hỏng.

### Chặn internal API

`InternalPathBlockingFilter` là `WebFilter` với `@Order(HIGHEST_PRECEDENCE)`: chạy **trước** CORS và trước khi match route, trả **404** cho mọi request có segment `internal`. So sánh trên giá trị đã decode nên `%69nternal` cũng bị chặn; segment `..` cũng bị chặn để tránh path traversal. Nhờ vậy toàn bộ `/api/v1/*/internal/**` không thể gọi từ Internet, kể cả preflight OPTIONS.

Đây là lớp phòng thủ thứ hai — lớp thứ nhất là header `X-Internal-Token` mà mỗi service yêu cầu.

### CORS

`CorsConfig` + `CorsProperties` (`app.cors.allowed-origins`, bind từ `${CORS_ALLOWED_ORIGINS}`, phân tách bằng dấu phẩy). Cho phép credentials, method GET/POST/PUT/DELETE/OPTIONS/PATCH, header `Origin, Content-Type, Accept, Authorization, X-Requested-With`, `max-age` 1 giờ. Các service phía sau tắt CORS để không sinh header trùng.

### Swagger gom

`springdoc.swagger-ui.urls` khai báo 6 mục trỏ tới `/aggregate/<svc>/v3/api-docs`. Mỗi service đặt `server.forward-headers-strategy: framework` nên tài liệu sinh ra dùng host của gateway, tức nút "Try it out" đi qua gateway thay vì gọi thẳng service.

### Test

`GatewayRoutingTest` (34 test, chạy offline với `eureka.client.enabled=false`): kiểm tra đúng tập route id, mọi route trỏ `lb://` đúng service, path công khai được route (502/503 vì không có instance), path không khai báo trả 404, **mọi biến thể của `/internal/` trả 404** (kể cả chữ hoa, percent-encoding, matrix param, path traversal, preflight), CORS cho origin hợp lệ và từ chối origin lạ, actuator health phục vụ tại chỗ. `ApiGatewayApplicationTests` kiểm tra context load.

### Hạn chế đã biết

- Không rate limit, không circuit breaker. Redis đã có sẵn trong hạ tầng nếu muốn thêm `RequestRateLimiter`.
- Không xác thực JWT tại edge (cố ý, xem `docs/03-bao-mat.md`).

---

## discovery-server (port 8761)

Eureka registry thuần (`@EnableEurekaServer`), không code nghiệp vụ.

| | |
|---|---|
| Port | `${SERVER_PORT:8761}` |
| Hostname | `${EUREKA_HOSTNAME:localhost}` (trong Docker là `discovery-server`) |
| Tự đăng ký | Không (`register-with-eureka: false`, `fetch-registry: false`) |
| Actuator | `health`, `info` — dùng làm healthcheck của container, các service khác `depends_on` nó |

**Lưu ý bảo mật**: Eureka không có xác thực. Bất kỳ ai truy cập được cổng 8761 đều đọc được registry và có thể đăng ký instance giả mạo. Trong docker-compose, cổng này chỉ nên mở trong mạng nội bộ; ở môi trường thật cần bọc Spring Security HTTP Basic và cấu hình client bằng `defaultZone: http://user:pass@host:8761/eureka/`. Đây là việc còn tồn đọng.
