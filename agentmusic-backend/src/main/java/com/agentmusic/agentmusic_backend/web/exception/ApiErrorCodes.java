package com.agentmusic.agentmusic_backend.web.exception;

public final class ApiErrorCodes {

    public static final String AUTHORIZATION = "spotify-authorization";
    public static final String AUTHORIZATION_MISSING = "spotify-authorization-missing";
    public static final String AUTHORIZATION_STATE = "spotify-authorization-state";
    public static final String SCOPE_MISSING = "spotify-scope-missing";
    public static final String BRIDGE_DISABLED = "spotify-bridge-disabled";
    public static final String DEVICE_UNAVAILABLE = "spotify-device-unavailable";
    public static final String DEVICE_OFFLINE = "spotify-device-offline";
    public static final String DEVICE_RESTRICTED = "spotify-device-restricted";
    public static final String NETWORK = "spotify-network";
    public static final String PLAYBACK_CONFLICT = "spotify-playback-conflict";
    public static final String INVALID_REQUEST = "invalid-request";
    public static final String NOT_FOUND = "not-found";
    public static final String SERVER_FAILURE = "server-failure";
    public static final String REQUEST_FAILURE = "request-failure";

    private ApiErrorCodes() {
    }
}
