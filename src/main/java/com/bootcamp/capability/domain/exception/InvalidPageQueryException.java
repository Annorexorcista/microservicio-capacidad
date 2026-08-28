package com.bootcamp.capability.domain.exception;

/**
 * Excepción de dominio lanzada cuando los parámetros de paginación u ordenamiento
 * del listado de capacidades no son válidos (page/size fuera de rango, o
 * sortBy/sortDirection con un valor no permitido).
 *
 * <p>Es una excepción pura, sin dependencias de HTTP; porta un
 * {@link PageErrorCode} que el handler global traduce al código de estado
 * correspondiente (400 Bad Request).
 */
public class InvalidPageQueryException extends RuntimeException {

    private final PageErrorCode code;

    public InvalidPageQueryException(PageErrorCode code) {
        super(code.getMessage());
        this.code = code;
    }

    /**
     * @return el código de error de negocio que originó esta excepción.
     */
    public PageErrorCode getCode() {
        return code;
    }
}
