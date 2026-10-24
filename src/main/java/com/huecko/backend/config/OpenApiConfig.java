package com.huecko.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Descripción de la API para Swagger UI (/api/docs). Con «Authorize» se pega
 * el JWT que devuelve /api/auth/login y se prueban los endpoints protegidos.
 */
@Configuration
public class OpenApiConfig {

    private static final String JWT = "jwt";

    @Bean
    public OpenAPI hueckoOpenApi() {
        // La del manifiesto del jar; al lanzar desde el IDE no la hay.
        String version = OpenApiConfig.class.getPackage().getImplementationVersion();
        return new OpenAPI()
                .info(new Info()
                        .title("Huecko API")
                        .version(version != null ? version : "dev")
                        .description("Horarios, grupos, planes y votaciones. Inicia sesión en "
                                + "POST /api/auth/login, copia el token y pégalo en «Authorize»."))
                // Relativo: detrás del proxy HTTPS de Render, springdoc generaría
                // http://… y el navegador bloquearía «Try it out».
                .addServersItem(new Server().url("/"))
                .components(new Components().addSecuritySchemes(JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(JWT));
    }
}
