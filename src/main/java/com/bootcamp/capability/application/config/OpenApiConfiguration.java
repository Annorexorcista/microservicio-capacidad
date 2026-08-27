package com.bootcamp.capability.application.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de la documentación OpenAPI del microservicio de capacidades.
 *
 * <p>Define el bean {@link OpenAPI} con los metadatos de la API (título, versión y
 * descripción). Junto con {@code springdoc-openapi-starter-webflux-ui}, expone la
 * especificación OpenAPI (en {@code /v3/api-docs}) y Swagger UI, describiendo el
 * endpoint de registro de capacidades y sus códigos de estado 201, 400, 409 y 502.
 * Cubre el Requerimiento 9.1.
 */
@Configuration
public class OpenApiConfiguration {

    private static final String API_TITLE = "Capability Service API";
    private static final String API_VERSION = "1.0.0";
    private static final String API_DESCRIPTION =
            "API del microservicio de capacidades. Expone el registro de capacidades, "
                    + "incluyendo el esquema de la solicitud, el esquema de la respuesta y los "
                    + "códigos de estado 201 (creada), 400 (datos inválidos), 409 (nombre duplicado) "
                    + "y 502 (Technology_Service no disponible).";

    /**
     * Bean con los metadatos de la especificación OpenAPI generada por springdoc.
     *
     * @return la definición {@link OpenAPI} con título, versión y descripción del servicio.
     */
    @Bean
    public OpenAPI capabilityOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(API_TITLE)
                        .version(API_VERSION)
                        .description(API_DESCRIPTION));
    }
}
