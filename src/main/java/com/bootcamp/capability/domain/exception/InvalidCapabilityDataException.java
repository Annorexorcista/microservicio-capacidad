package com.bootcamp.capability.domain.exception;

public class InvalidCapabilityDataException extends RuntimeException {

    private final DomainErrorCode code;

    public InvalidCapabilityDataException(DomainErrorCode code) {
        super(code.getMessage());
        this.code = code;
    }

    public DomainErrorCode getCode() {
        return code;
    }
}
