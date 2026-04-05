package com.socialnetwork.api_gateway.routes;

import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;

import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class GatewayRoutesConfig {

  @Bean
  public RouterFunction<ServerResponse> authServiceRoute() {
    return GatewayRouterFunctions.route("auth-service")
        .route(RequestPredicates.path("/api/v1/auth/**"), HandlerFunctions.http())
        .filter(lb("auth-service"))
        .build();
  }

  @Bean
  public RouterFunction<ServerResponse> authServiceSwaggerRoute() {
    return GatewayRouterFunctions.route("auth-service-swagger")
        .route(
            RequestPredicates.path("/aggregate/auth-service/v3/api-docs"), HandlerFunctions.http())
        .filter(lb("auth-service"))
        .filter(setPath("/v3/api-docs"))
        .build();
  }

  @Bean
  public RouterFunction<ServerResponse> userServiceRoute() {
    return GatewayRouterFunctions.route("user-service")
        .route(RequestPredicates.path("/api/v1/users/**"), HandlerFunctions.http())
        .filter(lb("user-service"))
        .build();
  }

  @Bean
  public RouterFunction<ServerResponse> userServiceSwaggerRoute() {
    return GatewayRouterFunctions.route("user-service-swagger")
        .route(
            RequestPredicates.path("/aggregate/user-service/v3/api-docs"), HandlerFunctions.http())
        .filter(lb("user-service"))
        .filter(setPath("/v3/api-docs"))
        .build();
  }

  @Bean
  public RouterFunction<ServerResponse> mediaServiceRoute() {
    return GatewayRouterFunctions.route("media-service")
        .route(
            RequestPredicates.path("/api/v1/media/**"),
            HandlerFunctions.http()) // Đường dẫn cho Post, Comment
        .filter(lb("media-service"))
        .build();
  }

  @Bean
  public RouterFunction<ServerResponse> mediaServiceSwaggerRoute() {
    return GatewayRouterFunctions.route("media-service-swagger")
        .route(
            RequestPredicates.path("/aggregate/media-service/v3/api-docs"), HandlerFunctions.http())
        .filter(lb("media-service"))
        .filter(setPath("/v3/api-docs"))
        .build();
  }

  @Bean
  public RouterFunction<ServerResponse> notificationServiceRoute() {
    return GatewayRouterFunctions.route("notification-service")
        .route(RequestPredicates.path("/api/v1/notifications/**"), HandlerFunctions.http())
        .route(RequestPredicates.path("/ws/**"), HandlerFunctions.http()) // MỞ ĐƯỜNG CHO WEBSOCKET
        .filter(lb("notification-service"))
        .build();
  }

  @Bean
  public RouterFunction<ServerResponse> notificationServiceSwaggerRoute() {
    return GatewayRouterFunctions.route("notification-service-swagger")
        .route(
            RequestPredicates.path("/aggregate/notification-service/v3/api-docs"), HandlerFunctions.http())
        .filter(lb("notification-service"))
        .filter(setPath("/v3/api-docs"))
        .build();
  }
}
