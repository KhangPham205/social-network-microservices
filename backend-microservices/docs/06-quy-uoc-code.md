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

## Bẫy đã gặp với Spring Boot 4 (ghi lại để khỏi mất thời gian)

1. **Kafka cần `spring-boot-starter-kafka`, không phải `org.springframework.kafka:spring-kafka`.** Boot 4 chuyển auto-configuration Kafka sang artifact riêng, chỉ được kéo vào bởi starter. Khai báo artifact thô thì `spring.kafka.*` **không bind**, không có `KafkaTemplate`, và mọi `@KafkaListener` im lặng không chạy. Bốn service từng dính lỗi này.
2. **`@WebMvcTest` + `@WithMockUser` không đủ.** Boot 4 không còn gắn cấu hình MockMvc của Spring Security vào slice, nên mọi request bị coi là ẩn danh và trả 401. Cách dùng trong repo: `.with(testSecurityContext())` trên request builder, hoặc dựng MockMvc thủ công với `MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity())`.
3. **`HttpComponentsClientHttpRequestFactory.setConnectTimeout(Duration)` đã bị bỏ** trong Spring Framework 7. Đặt connect timeout qua `ConnectionConfig` trên `PoolingHttpClientConnectionManager`; `responseTimeout` vẫn nằm ở `RequestConfig`.
4. **Lombok `@RequiredArgsConstructor` không chuyển `@Value` trên field sang constructor sinh ra.** Field `final` mang `@Value` sẽ khiến Spring đi tìm *bean* đúng kiểu đó và startup thất bại. Viết constructor tường minh và đặt `@Value` trên tham số.
5. **`rsql-jpa 6.0.33` không tương thích Hibernate 7 khi chạy H2.** `RSQLJPAAutoConfiguration` dò `DerbyDialect` vốn đã bị Hibernate 7 gỡ bỏ, gây `NoClassDefFoundError`. PostgreSQL được kiểm tra trước nên môi trường thật an toàn; profile test ghim `spring.jpa.database-platform` hoặc loại trừ auto-configuration đó.
6. **Xoá file nguồn không xoá `target/classes`.** Class cũ vẫn bị component scan và gây `BeanDefinitionOverrideException` hoặc `NoClassDefFoundError`. Sau khi xoá class, luôn `mvn clean` (hoặc `rm -rf <module>/target`) trước khi chạy lại.
