package com.bootcamp.capability.domain.exception;

import java.util.Collections;
import java.util.List;

public class TechnologiesNotFoundException extends RuntimeException {

    private final List<Long> missingIds;

    public TechnologiesNotFoundException(List<Long> missingIds) {
        super("Las siguientes tecnologías no existen: " + missingIds);
        this.missingIds = missingIds == null
                ? Collections.emptyList()
                : List.copyOf(missingIds);
    }

    public List<Long> getMissingIds() {
        return missingIds;
    }
}
