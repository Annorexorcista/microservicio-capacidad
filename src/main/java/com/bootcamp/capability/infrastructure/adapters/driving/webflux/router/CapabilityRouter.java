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

@Configuration
public class CapabilityRouter {

    private static final String CAPABILITIES_PATH = "/api/v1/capabilities";

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
                                                    defaultValue = "asc")),
                                    @Parameter(
                                            name = "ids",
                                            in = ParameterIn.QUERY,
                                            description = "Consulta por identificadores (CSV, p. ej. "
                                                    + "1,2,3) para el consumo entre microservicios. "
                                                    + "Si se proporciona, ignora la paginación y "
                                                    + "devuelve la lista de las capacidades indicadas "
                                                    + "(cada una con sus tecnologías id+nombre).",
                                            schema = @Schema(type = "string"))
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
                                            description = "Parámetros de paginación, ordenamiento o ids inválidos",
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
                            })),
            @RouterOperation(
                    path = CAPABILITIES_PATH,
                    method = RequestMethod.DELETE,
                    beanClass = ICapabilityServicePort.class,
                    beanMethod = "deleteCapabilitiesByIds",
                    operation = @Operation(
                            operationId = "deleteCapabilitiesByIds",
                            summary = "Elimina capacidades por identificadores (con cascada)",
                            description = "Elimina las capacidades cuyos identificadores se indican "
                                    + "en el parámetro de consulta 'ids' (separados por comas, por "
                                    + "ejemplo ?ids=1,2,3), junto con sus asociaciones, y elimina en "
                                    + "cascada las tecnologías que queden huérfanas (sin ninguna otra "
                                    + "capacidad que las referencie). Pensado para la eliminación en "
                                    + "cascada de un bootcamp.",
                            parameters = {
                                    @Parameter(
                                            name = "ids",
                                            in = ParameterIn.QUERY,
                                            required = true,
                                            description = "Identificadores de capacidad separados por comas",
                                            schema = @Schema(type = "string"))
                            },
                            responses = {
                                    @ApiResponse(
                                            responseCode = "204",
                                            description = "Capacidades eliminadas (sin contenido)"),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "El parámetro ids es obligatorio o contiene "
                                                    + "identificadores inválidos",
                                            content = @Content(
                                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class))),
                                    @ApiResponse(
                                            responseCode = "502",
                                            description = "El Technology_Service no está disponible para "
                                                    + "eliminar las tecnologías huérfanas",
                                            content = @Content(
                                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class)))
                            }))
    })
    public RouterFunction<ServerResponse> capabilityRoutes(CapabilityHandler handler) {
        return RouterFunctions.route()
                .POST(CAPABILITIES_PATH, accept(MediaType.APPLICATION_JSON), handler::register)
                .GET(CAPABILITIES_PATH, accept(MediaType.APPLICATION_JSON), handler::list)
                .DELETE(CAPABILITIES_PATH, handler::deleteByIds)
                .build();
    }
}
