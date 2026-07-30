package com.agentmusic.agentmusic_backend.web.exception;

import org.springframework.http.HttpStatus;

public class ApiRequestFailureException extends IllegalStateException {

    private final String code;
    private final HttpStatus status;

    public ApiRequestFailureException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ApiRequestFailureException(String code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
