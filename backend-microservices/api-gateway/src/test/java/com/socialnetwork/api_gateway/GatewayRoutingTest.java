package com.socialnetwork.api_gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialnetwork.api_gateway.config.CorsConfig;
import com.socialnetwork.api_gateway.routes.GatewayRoutesConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * API Gateway routing and CORS tests using {@link WebTestClient}.
 *
 * <p>Strategy: start a real reactive application context in a random port. All downstream services
 * (Eureka/auth-service) are stubbed at the property level so that the Gateway itself can start up
 * without external dependencies. Route definitions are loaded from {@link GatewayRoutesConfig} and
 * CORS headers from {@link CorsConfig}.
 *
 * <p>Because the real downstream services are not running we expect {@code 503 Service Unavailable}
 * on successful routes (the request reaches the gateway's load-balancer and fails to connect), or
 * {@code 404 Not Found} on unmatched paths. Both responses confirm that routing decisions were made
 * by the gateway itself.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      // No real Eureka – disable discovery
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      // Use simple in-memory reactive (no persistence needed)
      "spring.cloud.gateway.httpclient.connect-timeout=500",
      "spring.cloud.gateway.httpclient.response-timeout=1000ms",
    })
@ActiveProfiles("test")
class GatewayRoutingTest {

  @LocalServerPort private int port;

  private WebTestClient webTestClient;

  @Autowired private RouteLocator routeLocator;

  @BeforeEach
  void setUp() {
    this.webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
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
    List<String> routeIds =
        routeLocator.getRoutes().map(route -> route.getId()).collectList().block();

    assertThat(routeIds).contains("auth-service");
  }

  @Test
  @DisplayName("All expected route IDs are registered")
  void allExpectedRoutes_areRegistered() {
    List<String> routeIds =
        routeLocator.getRoutes().map(route -> route.getId()).collectList().block();

    assertThat(routeIds)
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
            "chat-service-swagger-ui",
            "chat-service-ws",
            "moderation-service",
            "moderation-service-swagger");
  }

  // ─────────────────────────────────────────────────────────────────
  //  2. Routing behaviour tests (downstream unavailable → 503/502 expected)
  // ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "/api/v1/auth/login is routed to auth-service (502 or 503 because LB cannot connect)")
  void authLoginPath_isRoutedToAuthService() {
    // The route exists so the gateway will attempt to forward the request.
    // Without a running auth-service instance, the response is 502 Bad Gateway or
    // 503 Service Unavailable — both confirm the request was routed (not 404).
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
  @DisplayName("/api/v1/users/* is routed to user-service (502 or 503)")
  void usersPath_isRoutedToUserService() {
    webTestClient
        .get()
        .uri("/api/v1/users/1")
        .exchange()
        .expectStatus()
        .value(
            status -> assertThat(status).as("Route should route to user-service").isIn(502, 503));
  }

  @Test
  @DisplayName("/api/v1/media/posts is routed to media-service (502 or 503)")
  void mediaPostsPath_isRoutedToMediaService() {
    webTestClient
        .get()
        .uri("/api/v1/media/posts")
        .exchange()
        .expectStatus()
        .value(
            status -> assertThat(status).as("Route should route to media-service").isIn(502, 503));
  }

  @Test
  @DisplayName("Unmatched path /api/v1/unknown → 404 Not Found (no matching route)")
  void unmatchedPath_returns404() {
    webTestClient
        .get()
        .uri("/api/v1/unknown/route/that/does/not/exist")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  // ─────────────────────────────────────────────────────────────────
  //  3. CORS configuration tests
  // ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("OPTIONS preflight from allowed origin returns CORS headers")
  void preflight_fromAllowedOrigin_returnsCorsHeaders() {
    webTestClient
        .options()
        .uri("/api/v1/auth/login")
        .header("Origin", "http://localhost:3000")
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "Content-Type")
        .exchange()
        // 200 OK or 204 No Content are both valid preflight responses
        .expectStatus()
        .value(status -> assertThat(status).isIn(200, 204))
        .expectHeader()
        .valueMatches("Access-Control-Allow-Origin", "http://localhost:3000")
        .expectHeader()
        .exists("Access-Control-Allow-Methods");
  }

  @Test
  @DisplayName(
      "OPTIONS preflight from allowed Vite dev-server origin (localhost:5173) → CORS headers present")
  void preflight_fromViteDevServer_returnsCorsHeaders() {
    webTestClient
        .options()
        .uri("/api/v1/users/profile")
        .header("Origin", "http://localhost:5173")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isIn(200, 204))
        .expectHeader()
        .valueMatches("Access-Control-Allow-Origin", "http://localhost:5173");
  }

  @Test
  @DisplayName("GET /api/v1/auth/** from allowed origin → Access-Control-Allow-Credentials: true")
  void request_fromAllowedOrigin_hasAllowCredentialsTrue() {
    // Real GET to a routed path with an allowed Origin header.
    // Even though the downstream is unavailable, the CORS filter runs first.
    webTestClient
        .get()
        .uri("/api/v1/auth/me")
        .header("Origin", "http://localhost:3000")
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
        .header("Origin", "http://localhost:3000")
        .header("Access-Control-Request-Method", "DELETE")
        .exchange()
        .expectHeader()
        .value(
            "Access-Control-Allow-Methods",
            methods ->
                assertThat(methods).contains("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
  }

  // ─────────────────────────────────────────────────────────────────
  //  4. Swagger aggregation route test
  // ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Swagger aggregation route /aggregate/auth-service/v3/api-docs is registered")
  void swaggerAggregateRoute_isRegisteredAndRouted() {
    webTestClient
        .get()
        .uri("/aggregate/auth-service/v3/api-docs")
        .exchange()
        .expectStatus()
        .value(
            status ->
                assertThat(status)
                    .as("Swagger route should forward (502/503), not 404")
                    .isIn(502, 503));
  }
}
