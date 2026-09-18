package com.socialnetwork.api_gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialnetwork.api_gateway.config.CorsConfig;
import com.socialnetwork.api_gateway.filter.InternalPathBlockingFilter;
import com.socialnetwork.api_gateway.routes.GatewayRoutesConfig;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * API Gateway routing, internal-path blocking and CORS tests using {@link WebTestClient}.
 *
 * <p>Strategy: start a real reactive application context on a random port with service discovery
 * disabled so the gateway starts without external dependencies. Route definitions come from {@link
 * GatewayRoutesConfig}, CORS from {@link CorsConfig} and the internal-path denylist from {@link
 * InternalPathBlockingFilter}.
 *
 * <p>Because no downstream service is running, a routed request ends in {@code 502 Bad Gateway} or
 * {@code 503 Service Unavailable} (the load balancer has no instance), while an unmatched or
 * blocked path ends in {@code 404 Not Found}. Both outcomes prove the decision was made by the
 * gateway itself.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      "app.cors.allowed-origins=http://localhost:3000,http://localhost:5173"
    })
class GatewayRoutingTest {

  private static final String ALLOWED_ORIGIN = "http://localhost:3000";
  private static final String VITE_ORIGIN = "http://localhost:5173";
  private static final String FORBIDDEN_ORIGIN = "https://evil.example";

  @LocalServerPort private int port;

  private WebTestClient webTestClient;

  @Autowired private RouteLocator routeLocator;

  @BeforeEach
  void setUp() {
    this.webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  private Map<String, Route> routesById() {
    return routeLocator
        .getRoutes()
        .collectList()
        .block()
        .stream()
        .collect(Collectors.toMap(Route::getId, Function.identity()));
  }

  // ─────────────────────────────────────────────────────────────────
  //  1. Route registration sanity checks (no HTTP calls needed)
  // ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Gateway context loads and RouteLocator bean is present")
  void contextLoads_andRouteLocatorIsNotNull() {
    assertThat(routeLocator).isNotNull();
  }

  @Test
  @DisplayName("Auth-service route is registered for /api/v1/auth/**")
  void authServiceRoute_isRegistered() {
    assertThat(routesById()).containsKey("auth-service");
  }

  @Test
  @DisplayName("Exactly the expected route IDs are registered (dead and broken routes removed)")
  void allExpectedRoutes_areRegistered() {
    assertThat(routesById().keySet())
        .containsExactlyInAnyOrder(
            "auth-service",
            "auth-service-swagger",
            "user-service",
            "user-service-swagger",
            "media-service",
            "media-service-swagger",
            "notification-service",
            "notification-service-swagger",
            "notification-ws",
            "chat-service",
            "chat-service-swagger",
            "chat-service-ws",
            "moderation-service",
            "moderation-service-swagger");
  }

  @Test
  @DisplayName("Every route targets the lb:// service id matching its name")
  void routes_targetLoadBalancedServiceIds() {
    Map<String, Route> routes = routesById();
    for (String svc :
        List.of(
            "auth-service",
            "user-service",
            "media-service",
            "notification-service",
            "chat-service",
            "moderation-service")) {
      assertThat(routes.get(svc).getUri()).hasToString("lb://" + svc);
      assertThat(routes.get(svc + "-swagger").getUri()).hasToString("lb://" + svc);
    }
    assertThat(routes.get("notification-ws").getUri()).hasToString("lb:ws://notification-service");
    assertThat(routes.get("chat-service-ws").getUri()).hasToString("lb:ws://chat-service");
  }

  // ─────────────────────────────────────────────────────────────────
  //  2. Routing behaviour tests (downstream unavailable → 502/503 expected)
  // ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "/api/v1/auth/login is routed to auth-service (502 or 503 because LB cannot connect)")
  void authLoginPath_isRoutedToAuthService() {
    webTestClient
        .post()
        .uri("/api/v1/auth/login")
        .exchange()
        .expectStatus()
        .value(
            status ->
                assertThat(status)
                    .as("Route should forward to auth-service (502 or 503)")
                    .isIn(502, 503));
  }

  @Test
  @DisplayName("/api/v1/users/1 is routed to user-service (502 or 503)")
  void usersPath_isRoutedToUserService() {
    webTestClient
        .get()
        .uri("/api/v1/users/1")
        .exchange()
        .expectStatus()
        .value(
            status -> assertThat(status).as("Route should route to user-service").isIn(502, 503));
  }

  @ParameterizedTest(name = "{0} is routed (502/503)")
  @ValueSource(
      strings = {
        "/api/v1/auth/admin/roles",
        "/api/v1/media/posts",
        "/api/v1/notifications",
        "/api/v1/chat/conversations",
        "/api/v1/moderation/reports",
        "/aggregate/auth-service/v3/api-docs",
        "/aggregate/chat-service/v3/api-docs/swagger-config"
      })
  void publicPrefixes_areRouted(String path) {
    webTestClient
        .get()
        .uri(path)
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).as("%s should be routed", path).isIn(502, 503));
  }

  @ParameterizedTest(name = "{0} is not routed (404)")
  @ValueSource(
      strings = {
        "/api/v1/unknown/route/that/does/not/exist",
        "/api/v1/reports/1",
        "/api/v1/complaints/1",
        "/aggregate/chat-service/swagger-ui/index.html"
      })
  void unmatchedPaths_return404(String path) {
    webTestClient.get().uri(path).exchange().expectStatus().isNotFound();
  }

  // ─────────────────────────────────────────────────────────────────
  //  3. Internal endpoints are never reachable through the gateway
  // ─────────────────────────────────────────────────────────────────

  @ParameterizedTest(name = "{0} → 404")
  @ValueSource(
      strings = {
        "/api/v1/users/internal/1",
        "/api/v1/auth/internal/credentials/1",
        "/api/v1/auth/internal/credentials/batch",
        "/api/v1/media/internal/posts/1/owner-id",
        "/api/v1/chat/internal/messages/abc",
        "/api/v1/users/INTERNAL/1",
        "/api/v1/users/%69nternal/1",
        "/api/v1/users/internal;x=1/1",
        "/api/v1/users/profile/../internal/1"
      })
  void internalPaths_areBlockedWith404(String path) {
    for (var method : List.of(webTestClient.get(), webTestClient.post(), webTestClient.put())) {
      method.uri(path).exchange().expectStatus().isNotFound();
    }
  }

  @Test
  @DisplayName("Blocked internal path returns 404 even for a CORS preflight")
  void internalPath_preflightIsBlocked() {
    webTestClient
        .options()
        .uri("/api/v1/users/internal/1")
        .header("Origin", ALLOWED_ORIGIN)
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  @DisplayName("Actuator health is served by the gateway itself, not routed")
  void actuatorHealth_isServedLocally() {
    webTestClient
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isIn(200, 503))
        .expectBody()
        .jsonPath("$.status")
        .exists();
  }

  // ─────────────────────────────────────────────────────────────────
  //  4. CORS configuration tests
  // ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("OPTIONS preflight from allowed origin returns CORS headers")
  void preflight_fromAllowedOrigin_returnsCorsHeaders() {
    webTestClient
        .options()
        .uri("/api/v1/auth/login")
        .header("Origin", ALLOWED_ORIGIN)
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "Content-Type")
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isIn(200, 204))
        .expectHeader()
        .valueMatches("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
        .expectHeader()
        .exists("Access-Control-Allow-Methods");
  }

  @Test
  @DisplayName("OPTIONS preflight from allowed Vite dev-server origin → CORS headers present")
  void preflight_fromViteDevServer_returnsCorsHeaders() {
    webTestClient
        .options()
        .uri("/api/v1/users/profile")
        .header("Origin", VITE_ORIGIN)
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isIn(200, 204))
        .expectHeader()
        .valueMatches("Access-Control-Allow-Origin", VITE_ORIGIN);
  }

  @Test
  @DisplayName("OPTIONS preflight from a disallowed origin → 403 without CORS headers")
  void preflight_fromForbiddenOrigin_isRejected() {
    webTestClient
        .options()
        .uri("/api/v1/auth/login")
        .header("Origin", FORBIDDEN_ORIGIN)
        .header("Access-Control-Request-Method", "POST")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .doesNotExist("Access-Control-Allow-Origin");
  }

  @Test
  @DisplayName("GET from allowed origin → Access-Control-Allow-Credentials: true")
  void request_fromAllowedOrigin_hasAllowCredentialsTrue() {
    webTestClient
        .get()
        .uri("/api/v1/auth/me")
        .header("Origin", ALLOWED_ORIGIN)
        .exchange()
        .expectHeader()
        .value(
            "Access-Control-Allow-Credentials", v -> assertThat(v).isEqualToIgnoringCase("true"));
  }

  @Test
  @DisplayName("Allowed methods include GET, POST, PUT, DELETE, OPTIONS, PATCH")
  void corsConfig_allowsAllExpectedMethods() {
    webTestClient
        .options()
        .uri("/api/v1/auth/login")
        .header("Origin", ALLOWED_ORIGIN)
        .header("Access-Control-Request-Method", "DELETE")
        .exchange()
        .expectHeader()
        .value(
            "Access-Control-Allow-Methods",
            methods ->
                assertThat(methods).contains("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
  }

  @Test
  @DisplayName("Preflight requesting the ngrok header is rejected (header no longer allowed)")
  void preflight_withNgrokHeader_isRejected() {
    webTestClient
        .options()
        .uri("/api/v1/auth/login")
        .header("Origin", ALLOWED_ORIGIN)
        .header("Access-Control-Request-Method", "GET")
        .header("Access-Control-Request-Headers", "ngrok-skip-browser-warning")
        .exchange()
        .expectStatus()
        .isForbidden();
  }
}
