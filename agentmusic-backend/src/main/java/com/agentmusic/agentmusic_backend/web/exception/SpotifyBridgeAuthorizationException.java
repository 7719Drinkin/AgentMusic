package com.agentmusic.agentmusic_backend.web.exception;

public class SpotifyBridgeAuthorizationException extends IllegalStateException {

    private final String code;

    public SpotifyBridgeAuthorizationException(String message) {
        this(ApiErrorCodes.AUTHORIZATION, message);
    }

    public SpotifyBridgeAuthorizationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
