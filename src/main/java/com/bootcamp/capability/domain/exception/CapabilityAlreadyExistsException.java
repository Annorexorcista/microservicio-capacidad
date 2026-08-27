package com.bootcamp.capability.domain.exception;

/**
 * Excepción de dominio lanzada cuando se intenta registrar una capacidad cuyo
 * nombre ya existe (comparación case-insensitive y con trim).
 * Es una excepción pura, sin dependencias de HTTP; el handler global la traduce
 * al código de estado correspondiente (409 Conflict).
 */
public class CapabilityAlreadyExistsException extends RuntimeException {

    public CapabilityAlreadyExistsException(String name) {
        super("El nombre '" + name + "' ya está registrado");
    }
}
