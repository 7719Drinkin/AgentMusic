package com.agentmusic.agentmusic_backend.dto;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;

public record UpdatePlaybackSessionRequest(
        String sessionId,
        String currentTrackId,
        String currentPlaylistId,
        Integer currentTrackIndex,
        Integer currentPositionMs,
        boolean isPlaying,
        PlaybackMode playbackMode,
        String deviceId
) {
}
