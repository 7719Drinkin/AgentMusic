package com.agentmusic.agentmusic_backend.client;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;

public record SpotifyPlaybackState(
        String trackId,
        Integer progressMs,
        boolean isPlaying,
        PlaybackMode playbackMode,
        String deviceId
) {
}

