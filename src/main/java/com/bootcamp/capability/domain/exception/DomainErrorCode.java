package com.bootcamp.capability.domain.exception;

/**
 * Códigos de error de negocio del dominio de capacidades.
 * Cada código asocia una regla de validación sintáctica (obligatoriedad,
 * longitudes, cantidad y no repetición de tecnologías) con su mensaje de
 * negocio, manteniendo los textos centralizados y libres de acoplamiento HTTP.
 */
public enum DomainErrorCode {

    NAME_REQUIRED("El nombre es obligatorio"),
    NAME_TOO_LONG("El nombre excede la longitud máxima de 50 caracteres"),
    DESCRIPTION_REQUIRED("La descripción es obligatoria"),
    DESCRIPTION_TOO_LONG("La descripción excede la longitud máxima de 90 caracteres"),
    TECHNOLOGIES_TOO_FEW("Una capacidad debe tener como mínimo 3 tecnologías"),
    TECHNOLOGIES_TOO_MANY("Una capacidad debe tener como máximo 20 tecnologías"),
    TECHNOLOGIES_DUPLICATED("No se permiten tecnologías repetidas");

    private final String message;

    DomainErrorCode(String message) {
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
