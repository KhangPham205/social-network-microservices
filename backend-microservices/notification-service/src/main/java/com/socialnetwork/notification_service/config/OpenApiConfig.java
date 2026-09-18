package com.socialnetwork.notification_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String SECURITY_SCHEME = "bearerAuth";

  @Bean
  public OpenAPI notificationServiceOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Notification Service API")
                .description("Notifications persisted from Kafka events and pushed over STOMP")
                .version("v1.0.0"))
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
        .components(
            new Components()
                .addSecuritySchemes(
                    SECURITY_SCHEME,
                    new SecurityScheme()
                        .name(SECURITY_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
