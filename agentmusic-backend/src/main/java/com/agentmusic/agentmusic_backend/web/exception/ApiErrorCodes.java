package com.agentmusic.agentmusic_backend.web.exception;

public final class ApiErrorCodes {

    public static final String AUTHORIZATION = "spotify-authorization";
    public static final String DEVICE_UNAVAILABLE = "spotify-device-unavailable";
    public static final String DEVICE_RESTRICTED = "spotify-device-restricted";
    public static final String INVALID_REQUEST = "invalid-request";
    public static final String NOT_FOUND = "not-found";
    public static final String REQUEST_FAILURE = "request-failure";

    private ApiErrorCodes() {
    }
}
