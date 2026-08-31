package com.bootcamp.capability.application.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    private static final String API_TITLE = "Capability Service API";
    private static final String API_VERSION = "1.0.0";
    private static final String API_DESCRIPTION =
            "API del microservicio de capacidades. Expone el registro de capacidades, "
                    + "incluyendo el esquema de la solicitud, el esquema de la respuesta y los "
                    + "códigos de estado 201 (creada), 400 (datos inválidos), 409 (nombre duplicado) "
                    + "y 502 (Technology_Service no disponible).";

    @Bean
    public OpenAPI capabilityOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(API_TITLE)
                        .version(API_VERSION)
                        .description(API_DESCRIPTION));
    }
}
