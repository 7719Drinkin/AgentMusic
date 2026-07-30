package com.agentmusic.agentmusic_backend.integration.spotify;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;

public record SpotifyPlaybackState(
        String trackId,
        Integer progressMs,
        boolean isPlaying,
        PlaybackMode playbackMode,
        String deviceId
) {
}

