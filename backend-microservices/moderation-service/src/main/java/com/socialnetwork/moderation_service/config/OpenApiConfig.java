package com.socialnetwork.moderation_service.config;

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

  @Bean
  public OpenAPI moderationServiceOpenAPI() {
    // Tên của scheme bảo mật (dùng nội bộ trong code)
    String securitySchemeName = "bearerAuth";

    return new OpenAPI()
        // Thêm cấu hình Server để Gateway có thể định tuyến đúng (Rất quan trọng trong
        // Microservices)
        .servers(List.of(new Server().url("/").description("Default Server URL")))
        .info(
            new Info()
                .title("Moderation Service API")
                .description("Tài liệu API cho module Moderation (Moderation Service)")
                .version("v1.0.0"))
        // Yêu cầu bảo mật cho toàn bộ các API
        .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
        // Định nghĩa ổ khóa JWT
        .components(
            new Components()
                .addSecuritySchemes(
                    securitySchemeName,
                    new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
