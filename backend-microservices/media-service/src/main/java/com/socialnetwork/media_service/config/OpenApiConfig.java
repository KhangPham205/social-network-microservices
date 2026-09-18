package com.socialnetwork.media_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String SECURITY_SCHEME = "bearerAuth";

  @Bean
  public OpenAPI mediaServiceOpenAPI() {
    return new OpenAPI()
        // Relative server URL so the docs work when aggregated behind the gateway.
        .servers(List.of(new Server().url("/").description("Default server URL")))
        .info(
            new Info()
                .title("Media Service API")
                .description("Posts, comments, reactions and recommendations")
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
