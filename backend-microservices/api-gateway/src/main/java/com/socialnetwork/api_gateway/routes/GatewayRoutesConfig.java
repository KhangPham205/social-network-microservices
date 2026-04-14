package com.socialnetwork.api_gateway.routes;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

  @Bean
  public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
    return builder.routes()

        // ================= AUTH =================
        .route("auth-service", r -> r.path("/api/v1/auth/**")
            .uri("lb://auth-service"))

        .route("auth-service-swagger", r -> r.path("/aggregate/auth-service/v3/api-docs/**")
            .filters(f -> f.setPath("/v3/api-docs"))
            .uri("lb://auth-service"))

        // ================= USER =================
        .route("user-service", r -> r.path("/api/v1/users/**")
            .uri("lb://user-service"))

        .route("user-service-swagger", r -> r.path("/aggregate/user-service/v3/api-docs/**")
            .filters(f -> f.setPath("/v3/api-docs"))
            .uri("lb://user-service"))

        // ================= MEDIA =================
        .route("media-service", r -> r.path("/api/v1/media/**")
            .uri("lb://media-service"))

        .route("media-service-swagger", r -> r.path("/aggregate/media-service/v3/api-docs/**")
            .filters(f -> f.setPath("/v3/api-docs"))
            .uri("lb://media-service"))

        // ================= NOTIFICATION =================
        .route("notification-service", r -> r.path("/api/v1/notifications/**")
            .uri("lb://notification-service"))

        .route("notification-service-swagger", r -> r.path("/aggregate/notification-service/v3/api-docs/**")
            .filters(f -> f.setPath("/v3/api-docs"))
            .uri("lb://notification-service"))

        // ================= NOTIFICATION WS =================
        .route("notification-ws", r -> r.path("/ws/**")
            .uri("lb:ws://notification-service"))

        .build();
  }
}