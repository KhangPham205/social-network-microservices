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
        .route(RequestPredicates.path("/api/users/**"), HandlerFunctions.http())
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
}
