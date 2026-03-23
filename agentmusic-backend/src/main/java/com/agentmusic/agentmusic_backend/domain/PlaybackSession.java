package com.agentmusic.agentmusic_backend.domain;

import java.time.LocalDateTime;

public record PlaybackSession(
        String id,
        String userId,
        String currentTrackId,
        Integer currentPositionMs,
        boolean isPlaying,
        PlaybackMode playbackMode,
        String deviceId,
        LocalDateTime lastUpdated,
        LocalDateTime expiresAt
) {
}

