package com.bootcamp.capability.domain.spi;

import com.bootcamp.capability.domain.model.TechnologySummary;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * Puerto de salida (spi) del dominio para la validación de existencia de
 * tecnologías en el microservicio de Tecnología.
 *
 * <p>Abstracción implementada por el adaptador driven basado en {@code WebClient}.
 * El dominio depende de este contrato y no del transporte HTTP, cumpliendo la
 * inversión de dependencias hexagonal.
 */
public interface ITechnologyGatewayPort {

    /**
     * Consulta al microservicio de Tecnología cuáles de los identificadores
     * proporcionados corresponden a tecnologías existentes.
     *
     * @param ids identificadores de tecnología a validar.
     * @return un {@link Flux} que emite únicamente los identificadores existentes
     *         (un subconjunto de los solicitados; 0..N elementos).
     */
    Flux<Long> findExistingTechnologyIds(Collection<Long> ids);

    /**
     * Consulta al microservicio de Tecnología los datos (id y nombre) de las
     * tecnologías correspondientes a los identificadores proporcionados, en una
     * única llamada por lotes (evita el problema N+1).
     *
     * @param ids identificadores de tecnología distintos a resolver.
     * @return un {@link Flux} que emite un {@link TechnologySummary} por cada
     *         tecnología existente devuelta por el service (0..N; subconjunto de
     *         los solicitados).
     */
    Flux<TechnologySummary> findTechnologiesByIds(Collection<Long> ids);

    /**
     * Solicita al microservicio de Tecnología eliminar las tecnologías indicadas.
     * Pensado para la eliminación en cascada: cuando se borran capacidades, las
     * tecnologías que quedan huérfanas (sin ninguna capacidad que las referencie)
     * se eliminan también.
     *
     * @param ids identificadores de tecnología a eliminar.
     * @return un {@link Mono} que completa cuando el borrado ha terminado.
     */
    Mono<Void> deleteTechnologiesByIds(Collection<Long> ids);
}
