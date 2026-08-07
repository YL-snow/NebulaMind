package com.nebulamind.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置
 *
 * 访问：
 *   - Swagger UI: http://localhost:8080/swagger-ui.html
 *   - OpenAPI JSON: http://localhost:8080/v3/api-docs
 *
 * 所有受保护接口需要 JWT Bearer Token。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI nebulamindOpenAPI() {
        final String securitySchemeName = "bearer-jwt";

        return new OpenAPI()
                .info(new Info()
                        .title("NebulaMind API")
                        .description("云盘智能体 - 后端 REST API 文档")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("NebulaMind Team")
                                .email("team@nebulamind.local"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("使用 JWT Token 鉴权，格式: Bearer <token>")));
    }
}
