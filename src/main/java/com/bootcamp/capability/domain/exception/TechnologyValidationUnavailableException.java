package com.bootcamp.capability.domain.exception;

/**
 * Excepción de dominio lanzada cuando no fue posible validar la existencia de
 * las tecnologías porque el Technology_Service no está disponible o respondió
 * con un error.
 * Es una excepción pura, sin dependencias de HTTP; el handler global la traduce
 * al código de estado correspondiente (502 Bad Gateway).
 */
public class TechnologyValidationUnavailableException extends RuntimeException {

    public TechnologyValidationUnavailableException(Throwable cause) {
        super("No fue posible validar las tecnologías", cause);
    }
}
