package com.bootcamp.capability.infrastructure.adapters.driving.webflux.handler;

import com.bootcamp.capability.domain.api.ICapabilityServicePort;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityRequest;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.mapper.CapabilityDtoMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Handler de la capa driving (WebFlux funcional) para el registro de capacidades.
 *
 * <p>Compone un pipeline reactivo de extremo a extremo, sin llamadas bloqueantes
 * ({@code .block()}): deserializa el cuerpo de la solicitud a {@link CapabilityRequest},
 * lo mapea al modelo de dominio, delega en el puerto de entrada
 * {@link ICapabilityServicePort}, mapea el resultado a DTO de respuesta y construye
 * la respuesta {@code 201 Created}.
 *
 * <p>El manejo de errores no ocurre aquí: cualquier {@code Mono.error} emitido por el
 * caso de uso (validación, unicidad, existencia de tecnologías) o por la
 * deserialización fluye por el pipeline y lo traduce el handler global de errores.
 *
 * <p>Se construye como bean en {@code BeanConfiguration}, por lo que la clase no lleva
 * la anotación {@code @Component} (evitando la creación de un bean duplicado). Las
 * dependencias se inyectan por constructor.
 */
public class CapabilityHandler {

    private final ICapabilityServicePort servicePort;
    private final CapabilityDtoMapper dtoMapper;

    /**
     * Crea el handler con sus colaboradores.
     *
     * @param servicePort puerto de entrada del dominio que ejecuta el registro.
     * @param dtoMapper   mapper entre DTOs de la capa web y el modelo de dominio.
     */
    public CapabilityHandler(ICapabilityServicePort servicePort, CapabilityDtoMapper dtoMapper) {
        this.servicePort = servicePort;
        this.dtoMapper = dtoMapper;
    }

    /**
     * Registra una capacidad a partir de la solicitud HTTP.
     *
     * <p>Pipeline reactivo: {@code bodyToMono -> map(toDomain) ->
     * flatMap(registerCapability) -> map(toResponse) -> flatMap(ServerResponse 201)}.
     * Los errores se propagan hacia el handler global; aquí no se capturan.
     *
     * @param request la solicitud del servidor con el cuerpo {@link CapabilityRequest}.
     * @return un {@link Mono} que emite la respuesta {@code 201 Created} con el DTO
     *         de la capacidad creada, o propaga el error correspondiente.
     */
    public Mono<ServerResponse> register(ServerRequest request) {
        return request.bodyToMono(CapabilityRequest.class)
                .map(dtoMapper::toDomain)
                .flatMap(servicePort::registerCapability)
                .map(dtoMapper::toResponse)
                .flatMap(response -> ServerResponse
                        .status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    /**
     * Lista las capacidades de forma paginada y ordenada a partir de los query
     * params de la solicitud.
     *
     * <p>Pipeline reactivo sin bloqueos: {@code fromCallable(toPageQuery) ->
     * flatMap(listCapabilities) -> map(toPageResponse) -> flatMap(ServerResponse 200)}.
     * El parseo de los query params se envuelve en {@code Mono.fromCallable} para
     * que un valor inválido emerja como {@code Mono.error} y lo traduzca el handler
     * global a 400. Los errores del dominio y del gateway se propagan igualmente.
     *
     * @param request la solicitud del servidor con los query params page, size,
     *                sortBy y sortDirection.
     * @return un {@link Mono} que emite la respuesta {@code 200 OK} con la página
     *         de capacidades, o propaga el error correspondiente.
     */
    public Mono<ServerResponse> list(ServerRequest request) {
        if (request.queryParam("ids").filter(v -> !v.isBlank()).isPresent()) {
            return findByIds(request);
        }
        return Mono.fromCallable(() -> dtoMapper.toPageQuery(request))
                .flatMap(servicePort::listCapabilities)
                .map(dtoMapper::toPageResponse)
                .flatMap(response -> ServerResponse
                        .ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    /**
     * Recupera las capacidades correspondientes al query param {@code ids} (CSV),
     * cada una con sus tecnologías (id + name). Pensado para el consumo entre
     * microservicios (por ejemplo, el de Bootcamp). Responde {@code 200 OK} con la
     * lista de capacidades; sin bloqueos. Un {@code ids} malformado se traduce a
     * 400 por el handler global.
     *
     * @param request la solicitud del servidor con el query param {@code ids}.
     * @return un {@link Mono} que emite la respuesta {@code 200 OK} con la lista.
     */
    private Mono<ServerResponse> findByIds(ServerRequest request) {
        return Mono.fromCallable(() -> dtoMapper.parseIds(request))
                .flatMapMany(servicePort::findCapabilitiesByIds)
                .map(dtoMapper::toListItemResponse)
                .collectList()
                .flatMap(list -> ServerResponse
                        .ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(list));
    }

    /**
     * Elimina las capacidades indicadas en el query param {@code ids} (CSV) y, en
     * cascada, las tecnologías que queden huérfanas. Pensado para el consumo entre
     * microservicios durante la eliminación de un bootcamp. Responde
     * {@code 204 No Content}; sin bloqueos. Un error del gateway de Tecnología se
     * traduce a 502 por el handler global.
     *
     * @param request la solicitud del servidor con el query param {@code ids}.
     * @return un {@link Mono} que emite la respuesta {@code 204 No Content}.
     */
    public Mono<ServerResponse> deleteByIds(ServerRequest request) {
        return Mono.fromCallable(() -> dtoMapper.parseIds(request))
                .flatMap(servicePort::deleteCapabilitiesByIds)
                .then(ServerResponse.noContent().build());
    }
}
