package com.bootcamp.capability.infrastructure.adapters.driving.webflux.exception;

public class InvalidIdsQueryException extends RuntimeException {

    private final RequestErrorCode code;

    public InvalidIdsQueryException(RequestErrorCode code, Object... arguments) {
        super(code.formatMessage(arguments));
        this.code = code;
    }

    public InvalidIdsQueryException(RequestErrorCode code, Throwable cause, Object... arguments) {
        super(code.formatMessage(arguments), cause);
        this.code = code;
    }

    public RequestErrorCode getCode() {
        return code;
    }
}
