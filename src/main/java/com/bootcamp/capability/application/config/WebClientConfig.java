package com.bootcamp.capability.application.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuración del cliente HTTP reactivo {@link WebClient} usado para consultar
 * al microservicio de Tecnología (Technology_Service).
 *
 * <p>Define un bean {@link WebClient} con la {@code baseUrl} del Technology_Service
 * parametrizada por la propiedad {@code technology.service.url} (variable de
 * entorno {@code TECHNOLOGY_SERVICE_URL}, por defecto {@code http://localhost:8080}),
 * tal como se declara en {@code application.yml}. Es no bloqueante y se compone
 * con el pipeline reactivo del {@code TechnologyGatewayAdapter} (Req 7.1, 8.3).
 */
@Configuration
public class WebClientConfig {

    private final String technologyServiceUrl;

    public WebClientConfig(
            @Value("${technology.service.url:http://localhost:8080}") String technologyServiceUrl) {
        this.technologyServiceUrl = technologyServiceUrl;
    }

    /**
     * Cliente HTTP reactivo apuntando al Technology_Service.
     *
     * @return el {@link WebClient} con la {@code baseUrl} del microservicio de Tecnología.
     */
    @Bean
    public WebClient technologyWebClient() {
        return WebClient.builder()
                .baseUrl(technologyServiceUrl)
                .build();
    }
}
