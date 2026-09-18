package com.socialnetwork.api_gateway.routes;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder.Builder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Route table of the public API. One REST route and one OpenAPI aggregation route per service,
 * plus the two STOMP WebSocket upgrades. Internal endpoints ({@code /internal/**}) are rejected
 * before routing by {@link com.socialnetwork.api_gateway.filter.InternalPathBlockingFilter}; the
 * gateway's own {@code /actuator/**} and {@code /swagger-ui.html} are intentionally not routed.
 */
@Configuration
public class GatewayRoutesConfig {

  private static final String AUTH_SERVICE = "auth-service";
  private static final String USER_SERVICE = "user-service";
  private static final String MEDIA_SERVICE = "media-service";
  private static final String NOTIFICATION_SERVICE = "notification-service";
  private static final String CHAT_SERVICE = "chat-service";
  private static final String MODERATION_SERVICE = "moderation-service";

  @Bean
  public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
    Builder routes = builder.routes();

    service(routes, AUTH_SERVICE, "/api/v1/auth/**");
    service(routes, USER_SERVICE, "/api/v1/users/**");
    service(routes, MEDIA_SERVICE, "/api/v1/media/**");
    service(routes, NOTIFICATION_SERVICE, "/api/v1/notifications/**");
    service(routes, CHAT_SERVICE, "/api/v1/chat/**");
    service(routes, MODERATION_SERVICE, "/api/v1/moderation/**");

    routes.route(
        "notification-ws",
        r -> r.path("/ws/notification/**").uri("lb:ws://" + NOTIFICATION_SERVICE));
    routes.route("chat-service-ws", r -> r.path("/ws/chat/**").uri("lb:ws://" + CHAT_SERVICE));

    return routes.build();
  }

  /**
   * Registers the REST route {@code <serviceId>} for {@code apiPrefix} and the OpenAPI aggregation
   * route {@code <serviceId>-swagger} that maps {@code /aggregate/<serviceId>/v3/api-docs/**} to
   * the service's own {@code /v3/api-docs/**} (sub-paths such as {@code /swagger-config} or group
   * documents are preserved).
   */
  private static void service(Builder routes, String serviceId, String apiPrefix) {
    String uri = "lb://" + serviceId;
    routes.route(serviceId, r -> r.path(apiPrefix).uri(uri));
    routes.route(
        serviceId + "-swagger",
        r ->
            r.path("/aggregate/" + serviceId + "/v3/api-docs/**")
                .filters(f -> f.rewritePath("/aggregate/" + serviceId + "/(?<rest>.*)", "/${rest}"))
                .uri(uri));
  }
}
