package com.bootcamp.capability.domain.exception;

import java.util.Collections;
import java.util.List;

/**
 * Excepción de dominio lanzada cuando al menos uno de los identificadores de
 * tecnología asociados a una capacidad no corresponde a una tecnología existente
 * en el Technology_Service.
 * Es una excepción pura, sin dependencias de HTTP; el handler global la traduce
 * al código de estado correspondiente (400 Bad Request), indicando cuáles
 * identificadores no existen.
 */
public class TechnologiesNotFoundException extends RuntimeException {

    private final List<Long> missingIds;

    public TechnologiesNotFoundException(List<Long> missingIds) {
        super("Las siguientes tecnologías no existen: " + missingIds);
        this.missingIds = missingIds == null
                ? Collections.emptyList()
                : List.copyOf(missingIds);
    }

    /**
     * @return los identificadores de tecnología que no fueron encontrados.
     */
    public List<Long> getMissingIds() {
        return missingIds;
    }
}
