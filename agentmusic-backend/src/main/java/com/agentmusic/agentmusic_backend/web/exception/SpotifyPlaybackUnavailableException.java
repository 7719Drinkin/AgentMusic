package com.agentmusic.agentmusic_backend.web.exception;

public class SpotifyPlaybackUnavailableException extends IllegalStateException {

    public SpotifyPlaybackUnavailableException(String message) {
        super(message);
    }
}
