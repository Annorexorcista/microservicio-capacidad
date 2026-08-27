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

/**
 * Cableado (wiring) de la arquitectura hexagonal.
 *
 * <p>Concentra en la capa de aplicación la construcción de los beans del dominio
 * y sus adaptadores, de modo que el núcleo ({@link CapabilityUseCase},
 * {@link com.bootcamp.capability.domain.model.Capability} y los puertos) y los
 * adaptadores driven permanecen como clases planas, libres de anotaciones de
 * Spring ({@code @Component}). Replica el enfoque de {@code microservicio_tecnologia}.
 *
 * <p>Los componentes ya gestionados por el framework se inyectan aquí:
 * {@link ICapabilityRepository} e {@link ICapabilityTechnologyRepository}
 * (repositorios reactivos de Spring Data), {@link CapabilityEntityMapper}
 * (anotado {@code @Component} en la tarea 6), {@link TransactionalOperator}
 * (definido en {@link R2dbcConfig}), {@link R2dbcEntityTemplate} (autoconfigurado
 * por Spring Data R2DBC) y {@link WebClient} (definido en {@link WebClientConfig}).
 *
 * <p>Nota: el bean del adaptador driving {@code CapabilityHandler} se añadirá a este
 * wiring en la tarea 9, cuando exista la clase del handler. Aquí solo se cablean el
 * adaptador de persistencia, el adaptador gateway y el caso de uso.
 */
@Configuration
public class BeanConfiguration {

    /**
     * Adaptador de persistencia R2DBC que implementa el puerto de salida
     * {@link ICapabilityPersistencePort}.
     *
     * @param capabilityRepository            repositorio reactivo de capacidades.
     * @param capabilityTechnologyRepository  repositorio reactivo de la tabla puente.
     * @param mapper                          mapper dominio<->entidad.
     * @param transactionalOperator           operador transaccional reactivo.
     * @param entityTemplate                  plantilla R2DBC para insertar las asociaciones.
     * @return el {@link CapabilityPersistenceAdapter} como {@link ICapabilityPersistencePort}.
     */
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

    /**
     * Adaptador gateway que implementa el puerto de salida
     * {@link ITechnologyGatewayPort} consultando al Technology_Service vía WebClient.
     *
     * @param technologyWebClient cliente reactivo apuntando al Technology_Service.
     * @return el {@link TechnologyGatewayAdapter} como {@link ITechnologyGatewayPort}.
     */
    @Bean
    public ITechnologyGatewayPort technologyGatewayPort(WebClient technologyWebClient) {
        return new TechnologyGatewayAdapter(technologyWebClient);
    }

    /**
     * Caso de uso del dominio, implementación del puerto de entrada
     * {@link ICapabilityServicePort}.
     *
     * @param persistencePort       puerto de persistencia.
     * @param technologyGatewayPort puerto de validación de existencia de tecnologías.
     * @return el {@link CapabilityUseCase} como {@link ICapabilityServicePort}.
     */
    @Bean
    public ICapabilityServicePort capabilityServicePort(
            ICapabilityPersistencePort persistencePort,
            ITechnologyGatewayPort technologyGatewayPort) {
        return new CapabilityUseCase(persistencePort, technologyGatewayPort);
    }

    /**
     * Handler de la capa driving (WebFlux funcional) que orquesta el registro de
     * capacidades. Se cablea aquí como clase plana (sin {@code @Component}),
     * replicando el enfoque de {@code microservicio_tecnologia}.
     *
     * @param capabilityServicePort puerto de entrada del dominio.
     * @param capabilityDtoMapper   mapper entre DTOs de la capa web y el dominio.
     * @return el {@link CapabilityHandler} listo para asociarse al router.
     */
    @Bean
    public CapabilityHandler capabilityHandler(
            ICapabilityServicePort capabilityServicePort,
            CapabilityDtoMapper capabilityDtoMapper) {
        return new CapabilityHandler(capabilityServicePort, capabilityDtoMapper);
    }
}
