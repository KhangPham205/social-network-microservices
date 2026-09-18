# 06 · Quy ước code

## Phân lớp trong mỗi service

```
com.socialnetwork.<svc>
├── <Svc>Application            # @SpringBootApplication (+ @EnableKafka khi cần), KHÔNG scanBasePackages
├── config/                     # SecurityConfig, OpenApiConfig, HttpClientConfig (RestClient builder + HTTP interfaces), Kafka error handler, WebSocketConfig
├── controller/                 # REST; chỉ map request/response, không nghiệp vụ; internal endpoints trong *InternalController dưới /internal
├── service/  + service/impl/   # interface + Impl (quy ước dự án); @Transactional ở đây
├── repository/                 # Spring Data; sub-package theo store (jpa/, mongo/, neo4j/) nếu >1 store
├── model/ (hoặc entity/)       # JPA entity extends common BaseEntity khi hợp lý; @Getter @Setter, @Enumerated(STRING)
├── dto/                        # request/response; validation annotations; record hoặc Lombok
├── client/                     # @HttpExchange interface tới service khác (paths khớp docs/services)
├── listener/ hoặc handler/     # @KafkaListener mỏng → gọi service
├── mapper/                     # MapStruct
└── enums/                      # enum riêng của service (enum dùng chung nằm ở common.vo)
```

## Quy tắc bắt buộc

1. **Contract dùng chung → `common`**. Không copy event/DTO/enum vào service.
2. **Không magic string**: topic → `KafkaTopics`; path → `ApiConstants`; role/claim/header → `SecurityConstants`; STOMP → `WebSocketConstants`.
3. **Exception**: chỉ ném `common.exception.*`; không `try/catch` nuốt lỗi trong controller/listener (listener để exception nổ để Kafka retry/DLT).
4. **Bảo mật**: `SecurityUtils.getCurrentUserId()`; kiểm tra quyền sở hữu/membership trong service layer; admin bằng `hasRole("ADMIN")`/`@PreAuthorize` với `@EnableMethodSecurity`.
5. **Entity**: `@Getter @Setter` (không `@Data`), `@Enumerated(EnumType.STRING)`, `@Builder.Default` khi có initializer, quan hệ LAZY + `@EntityGraph` khi list.
6. **Transaction & event**: publish Kafka sau commit; side effect ngoài DB (WS push, HTTP) cũng sau commit.
7. **HTTP client**: một `@LoadBalanced RestClient.Builder` (HttpClient5, connect 2s / response 5s) + `InternalTokenInterceptor`; interface `@HttpExchange`; timeout riêng cho ai-service.
8. **Cấu hình**: `${ENV:default}`; không `spring.profiles.active` trong file; không secret thật trong repo.
9. **Log**: `@Slf4j`, tiếng Anh, không log token/cookie/nội dung tin nhắn; INFO cho sự kiện nghiệp vụ, DEBUG cho chi tiết.
10. **Test**: mỗi module xanh offline (`mvn -o -pl <module> test`); unit test cho rule nghiệp vụ, `@WebMvcTest` cho security/JSON, `@EmbeddedKafka` cho listener khi cần.
11. **Format**: google-java-format (spotless plugin đã khai báo trong pluginManagement; chạy `mvn spotless:apply` khi cần), 2 space, LF (`.gitattributes`).
12. **Dependency**: version chỉ khai báo ở root `pom.xml` (`dependencyManagement`/properties); module không có `<version>`.

## Kiểm tra trước khi merge

- `mvn -q -DskipTests install` xanh toàn reactor.
- `mvn test` xanh (offline).
- Không còn `TODO` gây lỗi runtime, không code comment-out, không `System.out`.
- Đổi contract/endpoint/env → cập nhật `docs/` tương ứng và `CLAUDE.md` nếu ảnh hưởng quy ước.
