package com.agentmusic.agentmusic_backend.exception;

public class SpotifyPlaybackUnavailableException extends IllegalStateException {

    public SpotifyPlaybackUnavailableException(String message) {
        super(message);
    }
}
