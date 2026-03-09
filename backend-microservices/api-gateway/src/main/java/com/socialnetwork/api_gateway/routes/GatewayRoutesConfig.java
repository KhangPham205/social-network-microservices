package com.socialnetwork.api_gateway.routes;

import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;

@Configuration
public class GatewayRoutesConfig {

  @Bean
  public RouterFunction<ServerResponse> authServiceRoute() {
    return GatewayRouterFunctions.route("auth-service")
        .route(RequestPredicates.path("/api/auth/**"), HandlerFunctions.http())
        .filter(lb("auth-service")) // Chữ auth-service này phải khớp với tên trên Eureka
        .build();
  }
}