package com.socialnetwork.api_gateway.routes;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

  @Bean
  public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
    return builder
        .routes()

        // ================= AUTH =================
        .route("auth-service", r -> r.path("/api/v1/auth/**").uri("lb://auth-service"))
        .route(
            "auth-service-swagger",
            r ->
                r.path("/aggregate/auth-service/v3/api-docs/**")
                    .filters(f -> f.setPath("/v3/api-docs"))
                    .uri("lb://auth-service"))

        // ================= USER =================
        .route("user-service", r -> r.path("/api/v1/users/**").uri("lb://user-service"))
        .route(
            "user-service-swagger",
            r ->
                r.path("/aggregate/user-service/v3/api-docs/**")
                    .filters(f -> f.setPath("/v3/api-docs"))
                    .uri("lb://user-service"))

        // ================= MEDIA =================
        .route("media-service", r -> r.path("/api/v1/media/**").uri("lb://media-service"))
        .route(
            "media-service-swagger",
            r ->
                r.path("/aggregate/media-service/v3/api-docs/**")
                    .filters(f -> f.setPath("/v3/api-docs"))
                    .uri("lb://media-service"))

        // ================= NOTIFICATION =================
        .route(
            "notification-service",
            r -> r.path("/api/v1/notifications/**").uri("lb://notification-service"))
        .route(
            "notification-service-swagger",
            r ->
                r.path("/aggregate/notification-service/v3/api-docs/**")
                    .filters(f -> f.setPath("/v3/api-docs"))
                    .uri("lb://notification-service"))
        .route(
            "notification-ws",
            r -> r.path("/ws/notification/**").uri("lb:ws://notification-service"))

        // ================= CHAT =================
        .route("chat-service", r -> r.path("/api/v1/chat/**").uri("lb://chat-service"))
        .route(
            "chat-service-swagger",
            r ->
                r.path("/aggregate/chat-service/v3/api-docs/**")
                    .filters(f -> f.setPath("/v3/api-docs"))
                    .uri("lb://chat-service"))
        .route(
            "chat-service-swagger-ui",
            r ->
                r.path("/aggregate/chat-service/swagger-ui/**")
                    .filters(f -> f.setPath("/swagger-ui"))
                    .uri("lb://chat-service"))
        .route("chat-service-ws", r -> r.path("/ws/chat/**").uri("lb:ws://chat-service"))
        .route(
            "moderation-service",
            r ->
                r.path("/api/v1/moderation/**", "/api/v1/reports/**", "/api/v1/complaints/**")
                    .uri("lb://moderation-service"))
        .route(
            "moderation-service-swagger",
            r ->
                r.path("/aggregate/moderation-service/v3/api-docs/**")
                    .filters(f -> f.setPath("/v3/api-docs"))
                    .uri("lb://moderation-service"))
        .build();
  }
}
