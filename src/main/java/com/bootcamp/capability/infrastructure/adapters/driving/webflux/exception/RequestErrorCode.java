package com.bootcamp.capability.infrastructure.adapters.driving.webflux.exception;

public enum RequestErrorCode {

    IDS_REQUIRED("El parámetro 'ids' es obligatorio"),
    ID_EMPTY("El parámetro 'ids' contiene un valor vacío"),
    ID_NOT_NUMERIC("El identificador '%s' debe ser numérico"),
    ID_NOT_POSITIVE("El identificador '%s' debe ser mayor que cero"),
    ID_OUT_OF_RANGE("El identificador '%s' está fuera del rango permitido");

    private final String messageTemplate;

    RequestErrorCode(String messageTemplate) {
        this.messageTemplate = messageTemplate;
    }

    public String getCode() {
        return name();
    }

    public String formatMessage(Object... arguments) {
        return messageTemplate.formatted(arguments);
    }
}
