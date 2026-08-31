package com.bootcamp.capability.domain.exception;

public class CapabilityAlreadyExistsException extends RuntimeException {

    public CapabilityAlreadyExistsException(String name) {
        super("El nombre '" + name + "' ya está registrado");
    }
}
