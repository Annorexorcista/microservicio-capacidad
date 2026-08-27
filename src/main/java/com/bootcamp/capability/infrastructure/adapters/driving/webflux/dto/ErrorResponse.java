package com.bootcamp.capability.infrastructure.adapters.driving.webflux.dto;

import java.time.Instant;

/**
 * DTO uniforme para las respuestas de error de la capa driving (WebFlux).
 *
 * <p>Record inmutable que el handler global de errores produce al traducir una
 * excepción de dominio (o de entrada malformada) a una respuesta HTTP.
 *
 * @param status    código de estado HTTP de la respuesta de error.
 * @param code       código de negocio o de error que identifica la causa.
 * @param message    mensaje descriptivo del error.
 * @param timestamp  instante en que se generó la respuesta de error.
 */
public record ErrorResponse(int status, String code, String message, Instant timestamp) {
}
