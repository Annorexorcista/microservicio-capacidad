package com.bootcamp.capability.application.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    private final String technologyServiceUrl;

    public WebClientConfig(
            @Value("${technology.service.url:http://localhost:8080}") String technologyServiceUrl) {
        this.technologyServiceUrl = technologyServiceUrl;
    }

    @Bean
    public WebClient technologyWebClient() {
        return WebClient.builder()
                .baseUrl(technologyServiceUrl)
                .build();
    }
}
