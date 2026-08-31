package com.bootcamp.capability.application.config;

import com.bootcamp.capability.domain.api.ICapabilityServicePort;
import com.bootcamp.capability.domain.spi.ICapabilityPersistencePort;
import com.bootcamp.capability.domain.spi.ITechnologyGatewayPort;
import com.bootcamp.capability.domain.usecase.CapabilityUseCase;
import com.bootcamp.capability.infrastructure.adapters.driven.http.TechnologyGatewayAdapter;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.adapter.CapabilityPersistenceAdapter;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.mapper.CapabilityEntityMapper;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository.ICapabilityRepository;
import com.bootcamp.capability.infrastructure.adapters.driven.r2dbc.repository.ICapabilityTechnologyRepository;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.handler.CapabilityHandler;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.mapper.CapabilityDtoMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class BeanConfiguration {

    @Bean
    public ICapabilityPersistencePort capabilityPersistencePort(
            ICapabilityRepository capabilityRepository,
            ICapabilityTechnologyRepository capabilityTechnologyRepository,
            CapabilityEntityMapper mapper,
            TransactionalOperator transactionalOperator,
            R2dbcEntityTemplate entityTemplate) {
        return new CapabilityPersistenceAdapter(
                capabilityRepository,
                capabilityTechnologyRepository,
                mapper,
                transactionalOperator,
                entityTemplate);
    }

    @Bean
    public ITechnologyGatewayPort technologyGatewayPort(WebClient technologyWebClient) {
        return new TechnologyGatewayAdapter(technologyWebClient);
    }

    @Bean
    public ICapabilityServicePort capabilityServicePort(
            ICapabilityPersistencePort persistencePort,
            ITechnologyGatewayPort technologyGatewayPort) {
        return new CapabilityUseCase(persistencePort, technologyGatewayPort);
    }

    @Bean
    public CapabilityHandler capabilityHandler(
            ICapabilityServicePort capabilityServicePort,
            CapabilityDtoMapper capabilityDtoMapper) {
        return new CapabilityHandler(capabilityServicePort, capabilityDtoMapper);
    }
}
