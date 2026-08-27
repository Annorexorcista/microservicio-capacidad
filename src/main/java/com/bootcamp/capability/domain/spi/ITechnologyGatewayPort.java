package com.bootcamp.capability.domain.spi;

import reactor.core.publisher.Flux;

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
}
