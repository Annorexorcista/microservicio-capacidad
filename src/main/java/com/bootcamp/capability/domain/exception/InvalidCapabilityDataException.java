package com.bootcamp.capability.domain.exception;

/**
 * Excepción de dominio lanzada cuando los datos de una capacidad no superan
 * las validaciones sintácticas de negocio (obligatoriedad, longitudes, cantidad
 * y no repetición de tecnologías).
 * Es una excepción pura, sin dependencias de HTTP; porta un {@link DomainErrorCode}
 * que el handler global traduce al código de estado correspondiente (400 Bad Request).
 */
public class InvalidCapabilityDataException extends RuntimeException {

    private final DomainErrorCode code;

    public InvalidCapabilityDataException(DomainErrorCode code) {
        super(code.getMessage());
        this.code = code;
    }

    /**
     * @return el código de error de negocio que originó esta excepción.
     */
    public DomainErrorCode getCode() {
        return code;
    }
}
