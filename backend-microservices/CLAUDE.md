# CLAUDE.md

Guidance for AI assistants and new contributors working in this repository.
**Read `docs/` before changing code** — it is the source of truth and is kept up to date on purpose so nobody has to re-read the whole codebase.

| I need to know… | Read |
|---|---|
| What exists, which service owns what, how a request flows | `docs/01-kien-truc-tong-quan.md` |
| The shared library API (contracts, exceptions, security beans) | `docs/02-common-module.md` |
| Auth model, roles, internal API, WebSocket auth | `docs/03-bao-mat.md` |
| Kafka topics, event payloads, producer/consumer rules | `docs/04-kafka-events.md` |
| Env vars, profiles, how to run locally or in Docker | `docs/05-cau-hinh-va-trien-khai.md` |
| Package layout, coding rules, definition of done | `docs/06-quy-uoc-code.md` |
| One specific service in depth | `docs/services/<service>.md` |

## What this project is

A social-network backend: **Spring Cloud Gateway + Eureka + 6 Spring Boot 4.0.3 (Java 21) services + a Python FastAPI AI service**, communicating over REST (synchronous) and Kafka (asynchronous). Each service owns its own database.

Modules: `common` (shared library), `discovery-server`, `api-gateway`, `auth-service`, `user-service`, `media-service`, `notification-service`, `chat-service`, `moderation-service`, `ai-service` (Python).

## Build and test

```bash
mvn -q -DskipTests install      # build everything (installs `common` into ~/.m2 first)
mvn -pl <module> test           # test one module; add -o to stay offline
mvn -pl common install          # REQUIRED after changing anything in common
```

Tests must pass **without any infrastructure** (H2 + mocked external clients + `@EmbeddedKafka`). Never leave a red or deleted-without-replacement test.

## Non-negotiable rules

1. **Shared contracts live in `common`** (`com.socialnetwork.common.*`): Kafka event records, cross-service enums and DTOs, topic names, path constants, exceptions, JWT security. Never copy them into a service; never create a service-local duplicate of an event or enum.
2. **`common` is auto-configured.** Do not add `scanBasePackages`, `@Import` or `@ComponentScan` for it. Just declare `jwt.secret` and `app.internal.token`.
3. **No magic strings.** Topics → `KafkaTopics`; REST prefixes → `ApiConstants`; cookie/claim/header/role names → `SecurityConstants`; STOMP destinations → `WebSocketConstants`.
4. **Errors**: throw `common.exception.*` (`ResourceNotFoundException`, `BadRequestException`, `AccessDeniedException`, `InvalidCredentialsException`, `ConflictException`). `GlobalExceptionHandler` turns them into the shared `ErrorResponse`. Do not catch exceptions in controllers to build error responses by hand, and do not let a Kafka listener swallow failures — let them propagate so retry/DLT works.
5. **Security**: build the filter chain with `JwtSecurityConfigurer` and add `@EnableMethodSecurity`. Get the caller with `SecurityUtils.getCurrentUserId()`. Enforce ownership/membership in the service layer, not only in the controller.
6. **Service-to-service calls** go to `/api/v1/<svc>/internal/**` with the `X-Internal-Token` header (`InternalTokenInterceptor` on a `@LoadBalanced RestClient.Builder`). The gateway 404s any path containing `/internal/`, so these endpoints are unreachable from the internet.
7. **Kafka**: configure through YAML only (no hand-built producer/consumer factories), send the event object (never a pre-serialized string), key by aggregate id, publish **after the transaction commits**, and make consumers that write to the database idempotent.
8. **Configuration**: exactly `application.yaml` (with `${ENV_VAR:local-default}`) and `application-prod.yaml` per service. No `spring.profiles.active` inside the files, no secrets in the repo, no `application-dev.yaml`.
9. **Dependency versions** are declared only in the root `pom.xml`. Module POMs must not contain `<version>` tags.
10. **Entities**: `@Getter @Setter` (never `@Data`), `@Enumerated(EnumType.STRING)`, lazy associations plus `@EntityGraph` for list queries, extend `common.entity.BaseEntity` where it fits.
11. **Logging**: `@Slf4j`, English messages, never log tokens, cookies or message content.

## Conventions that already exist in this codebase (keep them)

- `Service` interface + `ServiceImpl` in `service/impl` — keep this even for single implementations.
- User-facing messages are Vietnamese; code, comments, log messages and docs in `docs/` follow the repo language rules (`docs/` is Vietnamese by request, code and logs are English).
- Formatting: google-java-format via `mvn spotless:apply`, 2-space indent, LF line endings (enforced by `.gitattributes`).

## When you change something

- Changing a shared contract → edit `common`, `mvn -pl common install`, then compile the whole reactor to find every break.
- Changing an endpoint, an event, or an environment variable → update the matching file in `docs/` in the same commit.
- Adding a service → add it to the root `pom.xml`, give it the standard `SecurityConfig`/config layout, document it in `docs/services/`, and add it to `docker/docker-compose.services.yml`.
