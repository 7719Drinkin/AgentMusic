package com.agentmusic.agentmusic_backend.web.exception;

public class SpotifyPlaybackUnavailableException extends IllegalStateException {

    private final String code;

    public SpotifyPlaybackUnavailableException(String message) {
        this(ApiErrorCodes.DEVICE_UNAVAILABLE, message);
    }

    public SpotifyPlaybackUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
