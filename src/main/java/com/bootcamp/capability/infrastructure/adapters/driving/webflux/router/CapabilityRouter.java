package com.bootcamp.capability.infrastructure.adapters.driving.webflux.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import com.bootcamp.capability.domain.api.ICapabilityServicePort;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityRequest;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityResponse;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.ErrorResponse;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.handler.CapabilityHandler;
import com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto.CapabilityPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Router de la capa driving (WebFlux funcional) que declara las rutas del
 * recurso {@code capabilities} y las asocia al {@link CapabilityHandler}.
 *
 * <p>Los endpoints funcionales ({@code RouterFunction}) no exponen su contrato
 * automáticamente a springdoc como lo hacen los {@code @RestController}. Por ello
 * la documentación OpenAPI del endpoint se declara de forma explícita con las
 * anotaciones {@link RouterOperations}/{@link RouterOperation} sobre el método
 * que produce el bean {@code RouterFunction}, describiendo el esquema de la
 * solicitud, el de la respuesta {@code 201} y los errores {@code 400}/{@code 409}/
 * {@code 502} (Requerimiento 9.1).
 */
@Configuration
public class CapabilityRouter {

    private static final String CAPABILITIES_PATH = "/api/v1/capabilities";

    /**
     * Declara la ruta {@code POST /api/v1/capabilities} (que acepta
     * {@code application/json}) y la delega en {@link CapabilityHandler#register}.
     *
     * @param handler handler que procesa el registro de capacidades.
     * @return la {@link RouterFunction} con la ruta de registro configurada.
     */
    @Bean
    @RouterOperations({
            @RouterOperation(
                    path = CAPABILITIES_PATH,
                    method = RequestMethod.POST,
                    beanClass = ICapabilityServicePort.class,
                    beanMethod = "registerCapability",
                    operation = @Operation(
                            operationId = "registerCapability",
                            summary = "Registra una nueva capacidad",
                            description = "Valida obligatoriedad y longitudes (nombre 1-50, "
                                    + "descripción 1-90), la cantidad (3-20) y no repetición de "
                                    + "las tecnologías asociadas, la unicidad del nombre "
                                    + "(case-insensitive) y la existencia de las tecnologías en "
                                    + "el Technology_Service, y persiste la capacidad.",
                            requestBody = @RequestBody(
                                    required = true,
                                    content = @Content(
                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                            schema = @Schema(implementation = CapabilityRequest.class))),
                            responses = {
                                    @ApiResponse(
                                            responseCode = "201",
                                            description = "Capacidad registrada correctamente",
                                            content = @Content(
                                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = CapabilityResponse.class))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "Datos inválidos (nombre/descripción "
                                                    + "obligatorios o exceden la longitud máxima, "
                                                    + "cantidad de tecnologías fuera de rango, "
                                                    + "tecnologías repetidas o inexistentes)",
                                            content = @Content(
                                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class))),
                                    @ApiResponse(
                                            responseCode = "409",
                                            description = "El nombre de la capacidad ya está registrado",
                                            content = @Content(
                                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class))),
                                    @ApiResponse(
                                            responseCode = "502",
                                            description = "No fue posible validar las tecnologías "
                                                    + "porque el Technology_Service no está disponible",
                                            content = @Content(
                                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class)))
                            })),
            @RouterOperation(
                    path = CAPABILITIES_PATH,
                    method = RequestMethod.GET,
                    beanClass = ICapabilityServicePort.class,
                    beanMethod = "listCapabilities",
                    operation = @Operation(
                            operationId = "listCapabilities",
                            summary = "Lista las capacidades de forma paginada y ordenada",
                            description = "Devuelve las capacidades paginadas (page, size) y "
                                    + "ordenadas por nombre o por la cantidad de tecnologías "
                                    + "asociadas, en dirección ascendente o descendente. Cada "
                                    + "capacidad incluye sus tecnologías con id y nombre, "
                                    + "resueltas con una única llamada por lotes al "
                                    + "Technology_Service.",
                            parameters = {
                                    @Parameter(
                                            name = "page",
                                            in = ParameterIn.QUERY,
                                            description = "Número de página (base cero). Default 0.",
                                            schema = @Schema(type = "integer", defaultValue = "0")),
                                    @Parameter(
                                            name = "size",
                                            in = ParameterIn.QUERY,
                                            description = "Tamaño de página (1-100). Default 10.",
                                            schema = @Schema(type = "integer", defaultValue = "10")),
                                    @Parameter(
                                            name = "sortBy",
                                            in = ParameterIn.QUERY,
                                            description = "Criterio de ordenamiento. Default name.",
                                            schema = @Schema(type = "string",
                                                    allowableValues = {"name", "technologyCount"},
                                                    defaultValue = "name")),
                                    @Parameter(
                                            name = "sortDirection",
                                            in = ParameterIn.QUERY,
                                            description = "Dirección de ordenamiento. Default asc.",
                                            schema = @Schema(type = "string",
                                                    allowableValues = {"asc", "desc"},
                                                    defaultValue = "asc"))
                            },
                            responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Página de capacidades",
                                            content = @Content(
                                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = CapabilityPageResponse.class))),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "Parámetros de paginación u ordenamiento inválidos",
                                            content = @Content(
                                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class))),
                                    @ApiResponse(
                                            responseCode = "502",
                                            description = "El Technology_Service no está disponible para "
                                                    + "enriquecer las tecnologías",
                                            content = @Content(
                                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class)))
                            }))
    })
    public RouterFunction<ServerResponse> capabilityRoutes(CapabilityHandler handler) {
        return RouterFunctions.route()
                .POST(CAPABILITIES_PATH, accept(MediaType.APPLICATION_JSON), handler::register)
                .GET(CAPABILITIES_PATH, accept(MediaType.APPLICATION_JSON), handler::list)
                .build();
    }
}
