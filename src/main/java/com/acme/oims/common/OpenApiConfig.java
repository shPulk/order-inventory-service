package com.acme.oims.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Order & Inventory Service", version = "0.1"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    @Bean
    OpenApiCustomizer standardErrorResponses() {
        return openApi -> openApi.getPaths().values().forEach(path ->
                path.readOperations().forEach(op -> {
                    add(op, "400", "Validation failed (body has an errors map)");
                    add(op, "401", "Missing, invalid or expired token");
                    add(op, "403", "Authenticated but role not allowed");
                    add(op, "404", "Resource not found (or not yours)");
                    add(op, "409", "Illegal state transition or concurrent modification");
                    add(op, "422", "Business rule violated (unknown product, insufficient stock)");
                }));
    }

    private static void add(Operation op, String code, String description) {
        if (op.getResponses().containsKey(code)) return;
        op.getResponses().addApiResponse(code, new ApiResponse().description(description)
                .content(new Content().addMediaType("application/problem+json",
                        new MediaType().schema(new ObjectSchema()))));
    }
}