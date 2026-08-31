package com.bootcamp.capability.domain.exception;

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

    public String getCode() {
        return name();
    }

    public String getMessage() {
        return message;
    }
}
