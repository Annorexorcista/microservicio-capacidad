package com.bootcamp.capability.domain.exception;

public class TechnologyValidationUnavailableException extends RuntimeException {

    public TechnologyValidationUnavailableException(Throwable cause) {
        super("No fue posible validar las tecnologías", cause);
    }
}
