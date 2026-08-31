package com.bootcamp.capability.domain.exception;

public class InvalidPageQueryException extends RuntimeException {

    private final PageErrorCode code;

    public InvalidPageQueryException(PageErrorCode code) {
        super(code.getMessage());
        this.code = code;
    }

    public PageErrorCode getCode() {
        return code;
    }
}
