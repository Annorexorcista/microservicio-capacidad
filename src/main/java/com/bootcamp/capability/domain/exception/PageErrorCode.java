package com.bootcamp.capability.domain.exception;

/**
 * Códigos de error de negocio para la validación de los parámetros de
 * paginación y ordenamiento del listado de capacidades.
 *
 * <p>Centraliza los mensajes libres de acoplamiento HTTP. El handler global
 * traduce {@link InvalidPageQueryException} (que porta uno de estos códigos) a
 * un 400 Bad Request.
 */
public enum PageErrorCode {

    PAGE_NEGATIVE("El parámetro page debe ser mayor o igual a 0"),
    SIZE_TOO_SMALL("El parámetro size debe ser mayor o igual a 1"),
    SIZE_TOO_LARGE("El parámetro size debe ser menor o igual a 100"),
    SORT_BY_INVALID("El criterio de ordenamiento es inválido (name, technologyCount)"),
    SORT_DIRECTION_INVALID("La dirección de ordenamiento es inválida (asc, desc)");

    private final String message;

    PageErrorCode(String message) {
        this.message = message;
    }

    /**
     * @return el código de negocio (nombre de la constante del enum).
     */
    public String getCode() {
        return name();
    }

    /**
     * @return el mensaje de negocio asociado al código de error.
     */
    public String getMessage() {
        return message;
    }
}
